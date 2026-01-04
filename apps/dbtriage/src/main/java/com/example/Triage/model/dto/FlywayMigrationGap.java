package com.example.Triage.model.dto;

import java.util.List;

/**
 * Represents missing Flyway migrations in target environment
 */
public record FlywayMigrationGap(
        boolean detectable,
        String message,
        List<FlywayHistoryRowDto> missingMigrations,
        Integer sourceLatestRank,
        Integer targetLatestRank) {
}

