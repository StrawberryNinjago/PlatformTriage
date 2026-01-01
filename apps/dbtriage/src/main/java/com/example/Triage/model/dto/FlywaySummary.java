package com.example.Triage.model.dto;

public record FlywaySummary(
                boolean historyTableExists,
                LatestApplied latestApplied,
                int failedCount) {
}
