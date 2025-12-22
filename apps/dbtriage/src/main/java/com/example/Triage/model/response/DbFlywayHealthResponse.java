package com.example.Triage.model.response;

import java.time.OffsetDateTime;

public record DbFlywayHealthResponse(
        FlywayStatus status,
        boolean historyTableExists,
        LatestApplied latestApplied,
        int failedCount,
        String message) {

    public enum FlywayStatus {
        HEALTHY,
        DEGRADED,
        FAILED,
        NOT_CONFIGURED
    }

    public record LatestApplied(
            Integer installedRank,
            String version,
            String description,
            String script,
            OffsetDateTime installedOn) {
    }
}

