package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record DiagnosticItem(String key, String value) {
}
