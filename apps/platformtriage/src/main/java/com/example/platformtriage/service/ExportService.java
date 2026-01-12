package com.example.platformtriage.service;

import com.example.common.export.ExportBundle;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.response.DeploymentSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for exporting platform diagnostics data.
 */
@Service
public class ExportService {
    
    /**
     * Convert deployment summary to export bundle format
     */
    public ExportBundle createExportBundle(DeploymentSummaryResponse summary) {
        // Build metadata
        ExportBundle.MetadataDto metadata = ExportBundle.MetadataDto.builder()
                .generatedAt(OffsetDateTime.now())
                .tool("PlatformTriage - Deployment Doctor")
                .toolVersion("1.0.0")
                .exportType("platform")
                .environment(determineEnvironment(summary))
                .identifier(buildIdentifier(summary))
                .build();
        
        // Build source
        Map<String, String> sourceDetails = new HashMap<>();
        if (summary.target().namespace() != null) {
            sourceDetails.put("namespace", summary.target().namespace());
        }
        if (summary.target().selector() != null) {
            sourceDetails.put("selector", summary.target().selector());
        }
        if (summary.target().release() != null) {
            sourceDetails.put("release", summary.target().release());
        }
        
        ExportBundle.SourceDto source = ExportBundle.SourceDto.builder()
                .type("kubernetes")
                .name(summary.target().namespace())
                .location(buildLocation(summary))
                .details(sourceDetails)
                .build();
        
        // Build health
        Map<String, Object> healthMetrics = new HashMap<>();
        healthMetrics.put("overallStatus", summary.health().overall().toString());
        healthMetrics.put("deploymentsReady", summary.health().deploymentsReady());
        if (summary.health().pods() != null) {
            healthMetrics.put("pods", summary.health().pods());
        }
        
        String healthSummary = buildHealthSummary(summary);
        
        ExportBundle.HealthDto health = ExportBundle.HealthDto.builder()
                .status(summary.health().overall().toString())
                .summary(healthSummary)
                .metrics(healthMetrics)
                .build();
        
        // Build findings
        List<ExportBundle.FindingDto> findings = summary.findings().stream()
                .map(this::convertFinding)
                .collect(Collectors.toList());
        
        // Build additional data
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("timestamp", summary.timestamp());
        if (summary.primaryFailure() != null) {
            additionalData.put("primaryFailure", summary.primaryFailure());
        }
        if (summary.topWarning() != null) {
            additionalData.put("topWarning", summary.topWarning());
        }
        if (summary.objects() != null) {
            additionalData.put("objects", summary.objects());
        }
        
        return ExportBundle.builder()
                .metadata(metadata)
                .source(source)
                .health(health)
                .findings(findings)
                .additionalData(additionalData)
                .build();
    }
    
    private ExportBundle.FindingDto convertFinding(Finding finding) {
        Map<String, Object> details = new HashMap<>();
        details.put("code", finding.code().name());
        details.put("owner", finding.owner().name());
        if (finding.evidence() != null && !finding.evidence().isEmpty()) {
            details.put("evidenceCount", finding.evidence().size());
            details.put("evidence", finding.evidence());
        }
        
        String recommendation = finding.nextSteps() != null && !finding.nextSteps().isEmpty()
                ? String.join("\n", finding.nextSteps())
                : null;
        
        return ExportBundle.FindingDto.builder()
                .id(finding.code().name())
                .type(finding.title())
                .severity(finding.severity().name())
                .category("deployment")
                .message(finding.explanation())
                .recommendation(recommendation)
                .details(details)
                .build();
    }
    
    private String determineEnvironment(DeploymentSummaryResponse summary) {
        // Try to infer environment from namespace or other indicators
        String namespace = summary.target().namespace();
        if (namespace != null) {
            if (namespace.contains("prod")) return "production";
            if (namespace.contains("stage") || namespace.contains("staging")) return "staging";
            if (namespace.contains("dev")) return "development";
            if (namespace.contains("test")) return "test";
        }
        return "unknown";
    }
    
    private String buildIdentifier(DeploymentSummaryResponse summary) {
        StringBuilder id = new StringBuilder();
        if (summary.target().namespace() != null) {
            id.append(summary.target().namespace());
        }
        if (summary.target().selector() != null) {
            id.append("-").append(summary.target().selector().replace("=", "-"));
        } else if (summary.target().release() != null) {
            id.append("-").append(summary.target().release());
        }
        return id.toString();
    }
    
    private String buildLocation(DeploymentSummaryResponse summary) {
        return String.format("Namespace: %s", summary.target().namespace());
    }
    
    private String buildHealthSummary(DeploymentSummaryResponse summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Overall Status: %s\n", summary.health().overall()));
        sb.append(String.format("Deployments Ready: %s\n", summary.health().deploymentsReady()));
        
        if (summary.health().pods() != null) {
            Map<String, Integer> pods = summary.health().pods();
            sb.append("Pods: ");
            sb.append(String.format("Running=%d, ", pods.getOrDefault("running", 0)));
            sb.append(String.format("Pending=%d, ", pods.getOrDefault("pending", 0)));
            sb.append(String.format("CrashLoop=%d, ", pods.getOrDefault("crashLoop", 0)));
            sb.append(String.format("ImagePullBackOff=%d, ", pods.getOrDefault("imagePullBackOff", 0)));
            sb.append(String.format("NotReady=%d", pods.getOrDefault("notReady", 0)));
        }
        
        return sb.toString();
    }
}
