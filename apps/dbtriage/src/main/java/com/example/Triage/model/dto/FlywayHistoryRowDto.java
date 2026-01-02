package com.example.Triage.model.dto;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record FlywayHistoryRowDto(
        Integer installedRank,
        String version,
        String description,
        String type,
        String script,
        String installedBy,
        OffsetDateTime installedOn,
        Integer executionTimeMs,
        Boolean success) {
}
