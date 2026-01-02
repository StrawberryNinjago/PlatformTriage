package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record FlywayWarningDto(
        String code,
        String message) {
}

