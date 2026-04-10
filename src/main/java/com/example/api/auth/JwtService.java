package com.example.api.auth;

import com.example.api.auth.AuthModels.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretKeyBytes;
    private final long expirationMinutes;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
        this.objectMapper = objectMapper;
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMinutes = expirationMinutes;
    }

    public TokenDetails generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.username());
        payload.put("patientId", user.patientId());
        payload.put("roles", user.roles());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("jti", tokenId);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signature = sign(encodedHeader + "." + encodedPayload);

        return new TokenDetails(
                encodedHeader + "." + encodedPayload + "." + signature,
                tokenId,
                expiresAt);
    }

    public ValidatedToken validateToken(String token) {
        String[] tokenParts = token.split("\\.");
        if (tokenParts.length != 3) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token structure");
        }

        String signingInput = tokenParts[0] + "." + tokenParts[1];
        String expectedSignature = sign(signingInput);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                tokenParts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token signature");
        }

        Map<String, Object> claims = decodePayload(tokenParts[1]);
        Instant expiresAt = Instant.ofEpochSecond(readLongClaim(claims, "exp"));
        if (expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
        }

        String username = String.valueOf(claims.get("sub"));
        Long patientId = readLongClaim(claims, "patientId");
        String tokenId = String.valueOf(claims.get("jti"));
        @SuppressWarnings("unchecked")
        List<String> roles = claims.containsKey("roles") ? (List<String>) claims.get("roles") : List.of();

        return new ValidatedToken(username, patientId, roles, tokenId, expiresAt);
    }

    private Map<String, Object> decodePayload(String encodedPayload) {
        try {
            byte[] decodedBytes = URL_DECODER.decode(encodedPayload);
            return objectMapper.readValue(decodedBytes, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token payload", exception);
        }
    }

    private long readLongClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token claim: " + claimName);
    }

    private String encodeJson(Map<String, Object> content) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(content));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize token content", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign token", exception);
        }
    }

    public record TokenDetails(String token, String tokenId, Instant expiresAt) {
    }

    public record ValidatedToken(String username, Long patientId, List<String> roles, String tokenId, Instant expiresAt) {
    }
}
