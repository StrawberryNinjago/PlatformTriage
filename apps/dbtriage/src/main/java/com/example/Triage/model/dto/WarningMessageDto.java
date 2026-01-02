package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record WarningMessageDto(String code, String message) {
}
