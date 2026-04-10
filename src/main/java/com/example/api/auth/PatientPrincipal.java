package com.example.api.auth;

import java.util.List;

public record PatientPrincipal(
        String username,
        Long patientId,
        List<String> roles) {
}
