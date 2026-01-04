package com.example.Triage.model.response;

import com.example.Triage.model.dto.*;
import com.example.Triage.model.enums.ComparisonMode;

import java.util.List;

/**
 * Complete response for environment comparison
 */
public record EnvironmentComparisonResponse(
        String sourceEnvironment,
        String targetEnvironment,
        String sourceIdentity,      // "DEV localhost:5433/cartdb (cart_user)"
        String targetIdentity,      // "PROD localhost:5434/cartdb (cart_user)"
        String schema,
        ComparisonMode comparisonMode,
        String modeBanner,
        ComparisonKPIs kpis,        // key metrics for quick assessment
        EnvironmentCapabilityMatrix sourceCapabilities,
        EnvironmentCapabilityMatrix targetCapabilities,
        List<DriftSection> driftSections,
        FlywayComparisonDto flywayComparison,
        FlywayMigrationGap flywayMigrationGap,  // missing migrations
        List<BlastRadiusItem> blastRadius,      // likely symptoms (sorted by risk)
        List<DiagnosticConclusion> conclusions,
        List<PrivilegeRequirement> missingPrivileges, // detailed privilege requirements
        String privilegeRequestSnippet,         // ready-to-send SQL
        String timestamp) {
}

