package com.example.Triage.service;

import com.example.Triage.model.response.ExportDiagnosticsResponse;
import com.example.common.export.ExportBundle;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for converting DB diagnostics to common export format.
 */
@Service
public class DbExportService {
    
    /**
     * Convert ExportDiagnosticsResponse to ExportBundle format
     */
    public ExportBundle convertToExportBundle(ExportDiagnosticsResponse response) {
        // Build metadata
        ExportBundle.MetadataDto metadata = ExportBundle.MetadataDto.builder()
                .generatedAt(response.metadata() != null ? 
                           response.metadata().generatedAt() : OffsetDateTime.now())
                .tool(response.metadata() != null ? 
                     response.metadata().tool() : "PlatformTriage - DB Doctor")
                .toolVersion("1.0.0")
                .exportType("db")
                .environment(response.metadata() != null ? 
                           response.metadata().environment() : "unknown")
                .identifier(response.metadata() != null ? 
                          response.metadata().connectionId() : "unknown")
                .build();
        
        // Build source
        Map<String, String> sourceDetails = new HashMap<>();
        if (response.db() != null) {
            sourceDetails.put("engine", response.db().engine());
            sourceDetails.put("host", response.db().host());
            sourceDetails.put("port", String.valueOf(response.db().port()));
            sourceDetails.put("database", response.db().database());
            sourceDetails.put("schema", response.db().schema());
            sourceDetails.put("username", response.db().username());
            sourceDetails.put("sslMode", response.db().sslMode());
        }
        
        ExportBundle.SourceDto source = ExportBundle.SourceDto.builder()
                .type("database")
                .name(response.db() != null ? response.db().database() : "unknown")
                .location(buildLocation(response))
                .details(sourceDetails)
                .build();
        
        // Build health
        Map<String, Object> healthMetrics = new HashMap<>();
        if (response.flyway() != null) {
            healthMetrics.put("flywayStatus", response.flyway().status());
            healthMetrics.put("currentVersion", response.flyway().currentVersion());
            healthMetrics.put("credentialDrift", response.flyway().credentialDrift());
        }
        if (response.schemaSummary() != null) {
            healthMetrics.put("tables", response.schemaSummary().tables());
            if (response.schemaSummary().indexes() != null) {
                healthMetrics.put("indexes", response.schemaSummary().indexes());
            }
            if (response.schemaSummary().constraints() != null) {
                healthMetrics.put("constraints", response.schemaSummary().constraints());
            }
        }
        
        String healthStatus = determineHealthStatus(response);
        String healthSummary = buildHealthSummary(response);
        
        ExportBundle.HealthDto health = ExportBundle.HealthDto.builder()
                .status(healthStatus)
                .summary(healthSummary)
                .metrics(healthMetrics)
                .build();
        
        // Build findings
        List<ExportBundle.FindingDto> findings = response.findings() != null ?
                response.findings().stream()
                        .map(this::convertFinding)
                        .collect(Collectors.toList()) :
                List.of();
        
        // Build additional data
        Map<String, Object> additionalData = new HashMap<>();
        if (response.flyway() != null) {
            additionalData.put("flyway", response.flyway());
        }
        if (response.compare() != null) {
            additionalData.put("comparison", response.compare());
        }
        
        return ExportBundle.builder()
                .metadata(metadata)
                .source(source)
                .health(health)
                .findings(findings)
                .additionalData(additionalData)
                .build();
    }
    
    private ExportBundle.FindingDto convertFinding(ExportDiagnosticsResponse.FindingDto finding) {
        Map<String, Object> details = new HashMap<>();
        details.put("type", finding.type());
        
        return ExportBundle.FindingDto.builder()
                .id(finding.type())
                .type(finding.type())
                .severity(finding.severity())
                .category("database")
                .message(finding.message())
                .recommendation(null)
                .details(details)
                .build();
    }
    
    private String determineHealthStatus(ExportDiagnosticsResponse response) {
        if (response.flyway() != null && response.flyway().credentialDrift() != null 
            && response.flyway().credentialDrift()) {
            return "WARNING";
        }
        if (response.findings() != null && !response.findings().isEmpty()) {
            boolean hasErrors = response.findings().stream()
                    .anyMatch(f -> "ERROR".equalsIgnoreCase(f.severity()));
            if (hasErrors) return "ERROR";
            return "WARNING";
        }
        return "HEALTHY";
    }
    
    private String buildLocation(ExportDiagnosticsResponse response) {
        if (response.db() == null) return "Unknown";
        return String.format("%s:%d/%s", 
                response.db().host(), 
                response.db().port(), 
                response.db().database());
    }
    
    private String buildHealthSummary(ExportDiagnosticsResponse response) {
        StringBuilder sb = new StringBuilder();
        
        if (response.flyway() != null) {
            sb.append(String.format("Flyway Status: %s\n", response.flyway().status()));
            if (response.flyway().currentVersion() != null) {
                sb.append(String.format("Current Version: %s\n", response.flyway().currentVersion()));
            }
            if (response.flyway().credentialDrift() != null && response.flyway().credentialDrift()) {
                sb.append("⚠️ Credential drift detected\n");
            }
        }
        
        if (response.schemaSummary() != null) {
            sb.append(String.format("Schema: %d tables", response.schemaSummary().tables()));
            if (response.schemaSummary().indexes() != null) {
                sb.append(String.format(", %d indexes", response.schemaSummary().indexes()));
            }
            if (response.schemaSummary().constraints() != null) {
                sb.append(String.format(", %d constraints", response.schemaSummary().constraints()));
            }
        }
        
        return sb.toString();
    }
}
