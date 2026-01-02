package com.example.Triage.model.dto;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record FlywayInstalledBySummaryDto(
        String installedBy,
        int appliedCount,
        OffsetDateTime lastSeen) {
}
