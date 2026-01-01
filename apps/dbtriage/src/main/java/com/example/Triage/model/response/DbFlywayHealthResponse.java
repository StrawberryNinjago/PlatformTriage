package com.example.Triage.model.response;

import com.example.Triage.model.dto.LatestApplied;
import com.example.Triage.model.enums.FlywayStatus;

public record DbFlywayHealthResponse(
        FlywayStatus status,
        boolean historyTableExists,
        LatestApplied latestApplied,
        int failedCount,
        String message) {
}
