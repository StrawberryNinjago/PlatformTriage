package com.example.Triage.model.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record AiTriageRequest(
        @NotBlank String tool,
        @NotBlank String question,
        String connectionId,
        String action,
        Map<String, Object> context
) {}
