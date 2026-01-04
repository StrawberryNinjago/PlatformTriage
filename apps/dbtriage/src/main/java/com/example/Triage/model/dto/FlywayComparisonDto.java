package com.example.Triage.model.dto;

/**
 * Flyway comparison between two environments
 */
public record FlywayComparisonDto(
        boolean available,
        String sourceLatestVersion,
        String targetLatestVersion,
        Integer sourceLatestRank,
        Integer targetLatestRank,
        boolean versionMatch,
        Integer sourceFailedCount,
        Integer targetFailedCount,
        String sourceInstalledBy,
        String targetInstalledBy,
        String message) {
}

