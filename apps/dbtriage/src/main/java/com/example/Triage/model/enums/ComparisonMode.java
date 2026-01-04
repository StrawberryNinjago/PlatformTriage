package com.example.Triage.model.enums;

/**
 * Represents the mode of environment comparison based on capability availability
 */
public enum ComparisonMode {
    FULL,      // Full schema comparison available
    PARTIAL,   // Partial comparison: limited metadata access
    BLOCKED    // Comparison cannot proceed: insufficient metadata access
}

