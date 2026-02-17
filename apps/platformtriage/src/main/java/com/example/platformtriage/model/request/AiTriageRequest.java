package com.example.platformtriage.model.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record AiTriageRequest(
        @NotBlank String tool,
        @NotBlank String question,
        String action,
        Map<String, Object> context,
        Map<String, Object> parameters
) {}
