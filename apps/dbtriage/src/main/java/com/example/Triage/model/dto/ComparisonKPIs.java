package com.example.Triage.model.dto;

/**
 * Key Performance Indicators for environment comparison
 */
public record ComparisonKPIs(
        int compatibilityErrors,
        int performanceWarnings,
        int missingMigrations,
        boolean hasCriticalIssues) {
}

