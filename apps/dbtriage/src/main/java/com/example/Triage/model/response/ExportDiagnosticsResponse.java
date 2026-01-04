package com.example.Triage.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record ExportDiagnosticsResponse(
        // Metadata
        MetadataDto metadata,
        
        // Connection Identity
        @JsonProperty("db")
        ConnectionDto db,
        
        // Flyway Health
        FlywayDto flyway,
        
        // Schema Summary (counts only)
        SchemaSummaryExportDto schemaSummary,
        
        // Environment Comparison (if used)
        CompareDto compare,
        
        // Findings
        List<FindingDto> findings
) {
    
    @Builder
    public record MetadataDto(
            OffsetDateTime generatedAt,
            String tool,
            String environment,
            String connectionId
    ) {}
    
    @Builder
    public record ConnectionDto(
            String engine,
            String host,
            Integer port,
            String database,
            String schema,
            String username,
            String sslMode
    ) {}
    
    @Builder
    public record FlywayDto(
            String status,
            String currentVersion,
            String installedBy,
            String lastMigration,
            Boolean credentialDrift
    ) {}
    
    @Builder
    public record SchemaSummaryExportDto(
            Integer tables,
            Integer indexes,
            Integer constraints
    ) {}
    
    @Builder
    public record CompareDto(
            Boolean enabled,
            String source,
            String target,
            Boolean flywayMismatch,
            Integer sourceVersion,
            Integer targetVersion
    ) {}
    
    @Builder
    public record FindingDto(
            String type,
            String severity,
            String message
    ) {}
}

