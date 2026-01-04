package com.example.Triage.model.dto;

import com.example.Triage.model.enums.DriftSeverity;
import com.example.Triage.model.enums.DriftStatus;

/**
 * Represents a single drift item in the comparison
 */
public record DriftItem(
        String category, // e.g., "TABLE", "COLUMN", "INDEX", "Compatibility", "Performance"
        String objectName, // e.g., "users", "users.email"
        String attribute, // e.g., "exists", "type", "not_null"
        Object sourceValue,
        Object targetValue,
        DriftStatus status,
        DriftSeverity severity,
        String riskLevel,  // "High", "Medium", "Low" for performance-related items
        String message) {
}
