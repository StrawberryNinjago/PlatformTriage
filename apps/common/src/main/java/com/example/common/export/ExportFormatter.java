package com.example.common.export;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Utility class for formatting export data into various formats.
 */
@Component
public class ExportFormatter {
    
    private final ObjectMapper objectMapper;
    
    public ExportFormatter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * Convert export bundle to JSON string
     */
    public String toJson(ExportBundle bundle) {
        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize export bundle to JSON", e);
        }
    }
    
    /**
     * Convert export bundle to Markdown format for JIRA/documentation
     */
    public String toMarkdown(ExportBundle bundle) {
        StringBuilder md = new StringBuilder();
        
        // Header
        md.append("# Diagnostic Export Report\n\n");
        
        // Metadata section
        if (bundle.metadata() != null) {
            md.append("## Metadata\n\n");
            md.append("- **Generated:** ").append(
                bundle.metadata().generatedAt().format(DateTimeFormatter.ISO_DATE_TIME)
            ).append("\n");
            md.append("- **Tool:** ").append(bundle.metadata().tool()).append("\n");
            md.append("- **Export Type:** ").append(bundle.metadata().exportType()).append("\n");
            if (bundle.metadata().environment() != null) {
                md.append("- **Environment:** ").append(bundle.metadata().environment()).append("\n");
            }
            if (bundle.metadata().identifier() != null) {
                md.append("- **Identifier:** ").append(bundle.metadata().identifier()).append("\n");
            }
            md.append("\n");
        }
        
        // Source section
        if (bundle.source() != null) {
            md.append("## Source\n\n");
            md.append("- **Type:** ").append(bundle.source().type()).append("\n");
            md.append("- **Name:** ").append(bundle.source().name()).append("\n");
            if (bundle.source().location() != null) {
                md.append("- **Location:** ").append(bundle.source().location()).append("\n");
            }
            if (bundle.source().details() != null && !bundle.source().details().isEmpty()) {
                md.append("\n**Details:**\n");
                for (Map.Entry<String, String> entry : bundle.source().details().entrySet()) {
                    md.append("- **").append(entry.getKey()).append(":** ")
                      .append(entry.getValue()).append("\n");
                }
            }
            md.append("\n");
        }
        
        // Health section
        if (bundle.health() != null) {
            md.append("## Health Status\n\n");
            md.append("**Status:** ").append(getStatusEmoji(bundle.health().status()))
              .append(" ").append(bundle.health().status()).append("\n\n");
            if (bundle.health().summary() != null) {
                md.append(bundle.health().summary()).append("\n\n");
            }
            if (bundle.health().metrics() != null && !bundle.health().metrics().isEmpty()) {
                md.append("**Metrics:**\n");
                for (Map.Entry<String, Object> entry : bundle.health().metrics().entrySet()) {
                    md.append("- **").append(entry.getKey()).append(":** ")
                      .append(entry.getValue()).append("\n");
                }
                md.append("\n");
            }
        }
        
        // Findings section
        if (bundle.findings() != null && !bundle.findings().isEmpty()) {
            md.append("## Findings\n\n");
            for (int i = 0; i < bundle.findings().size(); i++) {
                ExportBundle.FindingDto finding = bundle.findings().get(i);
                md.append("### ").append(i + 1).append(". ")
                  .append(getSeverityEmoji(finding.severity())).append(" ")
                  .append(finding.type()).append("\n\n");
                md.append("- **Severity:** ").append(finding.severity()).append("\n");
                if (finding.category() != null) {
                    md.append("- **Category:** ").append(finding.category()).append("\n");
                }
                md.append("- **Message:** ").append(finding.message()).append("\n");
                if (finding.recommendation() != null) {
                    md.append("- **Recommendation:** ").append(finding.recommendation()).append("\n");
                }
                md.append("\n");
            }
        }
        
        // Additional data section
        if (bundle.additionalData() != null && !bundle.additionalData().isEmpty()) {
            md.append("## Additional Data\n\n");
            md.append("```json\n");
            try {
                md.append(objectMapper.writeValueAsString(bundle.additionalData()));
            } catch (Exception e) {
                md.append("Error serializing additional data");
            }
            md.append("\n```\n");
        }
        
        return md.toString();
    }
    
    private String getStatusEmoji(String status) {
        if (status == null) return "❓";
        return switch (status.toUpperCase()) {
            case "HEALTHY", "OK" -> "✅";
            case "WARNING", "WARN" -> "⚠️";
            case "ERROR", "CRITICAL" -> "❌";
            default -> "ℹ️";
        };
    }
    
    private String getSeverityEmoji(String severity) {
        if (severity == null) return "ℹ️";
        return switch (severity.toUpperCase()) {
            case "INFO" -> "ℹ️";
            case "WARN", "WARNING" -> "⚠️";
            case "ERROR" -> "❌";
            case "CRITICAL" -> "🔥";
            default -> "ℹ️";
        };
    }
}
