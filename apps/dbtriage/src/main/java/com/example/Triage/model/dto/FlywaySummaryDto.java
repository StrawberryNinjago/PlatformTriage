package com.example.Triage.model.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record FlywaySummaryDto(
                boolean historyTableExists,
                LatestAppliedDto latestApplied,
                int failedCount,
                List<FlywayInstalledBySummaryDto> installedBySummary) {
}
