package com.example.common.export;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * Generic export bundle for diagnostic data.
 * Can be used for both database and platform diagnostics.
 */
@Builder
public record ExportBundle(
        // Metadata about the export
        MetadataDto metadata,
        
        // Source information (database, cluster, etc.)
        SourceDto source,
        
        // Health status
        HealthDto health,
        
        // Findings/warnings/errors
        List<FindingDto> findings,
        
        // Additional data (flexible)
        Map<String, Object> additionalData
) {
    
    @Builder
    public record MetadataDto(
            OffsetDateTime generatedAt,
            String tool,
            String toolVersion,
            String exportType, // "db", "platform", "full"
            String environment,
            String identifier
    ) {}
    
    @Builder
    public record SourceDto(
            String type, // "database", "kubernetes", etc.
            String name,
            String location,
            Map<String, String> details
    ) {}
    
    @Builder
    public record HealthDto(
            String status, // "HEALTHY", "WARNING", "ERROR"
            String summary,
            Map<String, Object> metrics
    ) {}
    
    @Builder
    public record FindingDto(
            String id,
            String type,
            String severity, // "INFO", "WARN", "ERROR", "CRITICAL"
            String category, // "configuration", "performance", "security", etc.
            String message,
            String recommendation,
            Map<String, Object> details
    ) {}
}
