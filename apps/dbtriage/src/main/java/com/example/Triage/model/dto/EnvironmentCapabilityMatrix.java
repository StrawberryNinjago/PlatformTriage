package com.example.Triage.model.dto;

/**
 * Capability matrix for a single environment (e.g., DEV or PROD)
 */
public record EnvironmentCapabilityMatrix(
        String environmentName,
        String connectionId,
        CapabilityStatus connect,
        CapabilityStatus identity,
        CapabilityStatus tables,
        CapabilityStatus columns,
        CapabilityStatus constraints,
        CapabilityStatus indexes,
        CapabilityStatus flywayHistory,
        CapabilityStatus grants) {
}

