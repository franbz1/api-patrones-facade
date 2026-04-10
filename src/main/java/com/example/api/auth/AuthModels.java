package com.example.api.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;
import java.util.List;

public final class AuthModels {

    private AuthModels() {
    }

    public record LoginRequest(
            @JsonAlias({"username", "usuario"}) String username,
            @JsonAlias({"password", "contrasena"}) String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            String username,
            List<String> roles) {
    }

    public record LogoutResponse(String message, Instant loggedOutAt) {
    }

    public record AuthenticatedUser(
            String username,
            String password,
            List<String> roles) {
    }
}
