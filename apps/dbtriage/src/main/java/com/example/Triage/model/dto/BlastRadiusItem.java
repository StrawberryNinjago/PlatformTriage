package com.example.Triage.model.dto;

import java.util.List;

/**
 * Represents the blast radius (likely symptoms) of a drift item
 */
public record BlastRadiusItem(
        String objectName,
        String driftType,      // e.g., "Missing column", "Missing index"
        String driftSubtype,   // e.g., "Columns differ", "Method differs: btree vs gin"
        String category,       // e.g., "Compatibility", "Performance"
        String riskLevel,      // e.g., "High", "Medium", "Low"
        List<String> likelySymptoms,
        boolean isGroupRepresentative, // true if this represents multiple similar items
        int groupCount) {      // number of items in group if representative
}

