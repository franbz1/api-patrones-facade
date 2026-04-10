package com.example.api.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;
import java.util.List;

public final class AuthModels {

    private AuthModels() {
    }

    public record LoginRequest(
            @JsonAlias({"identifier", "username", "usuario", "document", "documento"}) String identifier,
            @JsonAlias({"password", "contrasena"}) String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            String username,
            Long patientId,
            List<String> roles) {
    }

    public record LogoutResponse(String message, Instant loggedOutAt) {
    }

    public record AuthenticatedUser(
            String username,
            String password,
            Long patientId,
            List<String> roles) {
    }
}
