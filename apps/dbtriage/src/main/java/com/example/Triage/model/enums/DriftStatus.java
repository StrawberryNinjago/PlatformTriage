package com.example.Triage.model.enums;

/**
 * Three-valued truth model for drift detection
 */
public enum DriftStatus {
    MATCH,      // Values match
    DIFFER,     // Values differ
    UNKNOWN     // Cannot determine due to privilege limitations
}

