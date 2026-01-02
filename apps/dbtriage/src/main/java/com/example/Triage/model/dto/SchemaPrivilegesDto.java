package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record SchemaPrivilegesDto(
        String schema,
        boolean canUsage,
        boolean canCreate) {
}
