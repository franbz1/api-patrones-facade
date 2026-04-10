package com.example.api.auth;

import com.example.api.auth.AuthModels.AuthenticatedUser;
import com.example.api.auth.AuthModels.LoginRequest;
import com.example.api.auth.AuthModels.LoginResponse;
import com.example.api.auth.AuthModels.LogoutResponse;
import com.example.api.auth.JwtService.TokenDetails;
import com.example.api.auth.JwtService.ValidatedToken;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final Map<String, AuthenticatedUser> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, Instant> revokedTokenExpirations = new ConcurrentHashMap<>();
    private final JwtService jwtService;

    public AuthService(JwtService jwtService) {
        this.jwtService = jwtService;
        seedUsers();
    }

    public LoginResponse login(LoginRequest request) {
        String username = requireText(request.username(), "username");
        String password = requireText(request.password(), "password");

        AuthenticatedUser user = Optional.ofNullable(usersByUsername.get(username))
                .filter(candidate -> candidate.password().equals(password))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        TokenDetails tokenDetails = jwtService.generateToken(user);
        return new LoginResponse(
                tokenDetails.token(),
                "Bearer",
                tokenDetails.expiresAt(),
                user.username(),
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

    private void seedUsers() {
        usersByUsername.put("admin", new AuthenticatedUser("admin", "admin123", List.of("ROLE_ADMIN")));
        usersByUsername.put("doctor", new AuthenticatedUser("doctor", "doctor123", List.of("ROLE_DOCTOR")));
        usersByUsername.put("patient", new AuthenticatedUser("patient", "patient123", List.of("ROLE_PATIENT")));
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
