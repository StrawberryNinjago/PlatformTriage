package com.example.Triage.model.response;

import java.time.OffsetDateTime;
import java.util.List;

public record DbSummaryResponse(
        DbIdentity db,
        FlywaySummary flyway,
        PublicSchemaSummary publicSchema) {
    public record DbIdentity(
            String database,
            String user,
            String serverAddr,
            Integer serverPort,
            String serverVersion,
            OffsetDateTime serverTime) {
    }

    public record FlywaySummary(
            boolean historyTableExists,
            LatestApplied latestApplied,
            int failedCount) {
        public record LatestApplied(
                Integer installedRank,
                String version,
                String description,
                String script,
                OffsetDateTime installedOn) {
        }
    }

    public record PublicSchemaSummary(
            int tableCount,
            List<TableExistence> importantTables) {
        public record TableExistence(String name, boolean exists) {
        }
    }
}