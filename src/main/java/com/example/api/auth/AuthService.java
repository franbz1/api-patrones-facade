package com.example.api.auth;

import com.example.api.auth.AuthModels.AuthenticatedUser;
import com.example.api.auth.AuthModels.LoginRequest;
import com.example.api.auth.AuthModels.LoginResponse;
import com.example.api.auth.AuthModels.LogoutResponse;
import com.example.api.auth.JwtService.TokenDetails;
import com.example.api.auth.JwtService.ValidatedToken;
import com.example.api.clinic.ClinicDomain;
import com.example.api.clinic.ClinicDomain.Patient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final Map<String, AuthenticatedUser> usersByDocument = new ConcurrentHashMap<>();
    private final Map<String, Instant> revokedTokenExpirations = new ConcurrentHashMap<>();
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerPatientAccess(Patient patient, String rawPassword) {
        String document = ClinicDomain.requireText(patient.document(), "document");
        String normalizedDocument = ClinicDomain.normalize(document);
        if (usersByDocument.containsKey(normalizedDocument)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient credentials already exist");
        }

        usersByDocument.put(
                normalizedDocument,
                new AuthenticatedUser(
                        document,
                        passwordEncoder.encode(requireText(rawPassword, "password")),
                        patient.id(),
                        List.of("ROLE_PATIENT")));
    }

    public LoginResponse login(LoginRequest request) {
        String username = requireText(request.identifier(), "identifier");
        String password = requireText(request.password(), "password");
        String normalizedDocument = ClinicDomain.normalize(username);

        AuthenticatedUser user = Optional.ofNullable(usersByDocument.get(normalizedDocument))
                .filter(candidate -> passwordEncoder.matches(password, candidate.password()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        TokenDetails tokenDetails = jwtService.generateToken(user);
        return new LoginResponse(
                tokenDetails.token(),
                "Bearer",
                tokenDetails.expiresAt(),
                user.username(),
                user.patientId(),
                user.roles());
    }

    public LogoutResponse logout(String token) {
        ValidatedToken validatedToken = jwtService.validateToken(token);
        revokedTokenExpirations.put(validatedToken.tokenId(), validatedToken.expiresAt());
        purgeExpiredRevocations();
        return new LogoutResponse("Logout successful", Instant.now());
    }

    public ValidatedToken authenticate(String token) {
        purgeExpiredRevocations();
        ValidatedToken validatedToken = jwtService.validateToken(token);
        if (revokedTokenExpirations.containsKey(validatedToken.tokenId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has been revoked");
        }
        return validatedToken;
    }

    private void purgeExpiredRevocations() {
        Instant now = Instant.now();
        revokedTokenExpirations.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }
}
