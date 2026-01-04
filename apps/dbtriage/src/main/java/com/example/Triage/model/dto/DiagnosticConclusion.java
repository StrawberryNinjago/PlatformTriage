package com.example.Triage.model.dto;

import com.example.Triage.model.enums.DriftSeverity;
import java.util.List;

/**
 * Human-readable diagnostic conclusion derived from drift analysis
 * Enhanced with evidence-based structure for defendable conclusions
 */
public record DiagnosticConclusion(
        DriftSeverity severity,
        String category,      // e.g., "Compatibility", "Performance", "Migration"
        String finding,       // one sentence, specific finding
        List<String> evidence, // bullet list of concrete evidence
        String impact,        // short impact statement
        String recommendation, // general recommendation
        List<NextAction> nextActions) { // actionable next steps
}

