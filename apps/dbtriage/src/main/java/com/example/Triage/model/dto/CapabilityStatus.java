package com.example.Triage.model.dto;

/**
 * Represents the status of a specific capability check
 */
public record CapabilityStatus(
        boolean available,
        String message,
        String diagnosticCode,
        String missingPrivilege, // e.g., "SELECT on pg_catalog.pg_indexes"
        String permissionDenied) // specific permission denied error if any
{

    public static CapabilityStatus createAvailable() {
        return new CapabilityStatus(true, "Available", null, null, null);
    }

    public static CapabilityStatus createUnavailable(String message, String diagnosticCode) {
        return new CapabilityStatus(false, message, diagnosticCode, null, null);
    }

    public static CapabilityStatus createUnavailable(String message, String diagnosticCode,
            String missingPrivilege, String permissionDenied) {
        return new CapabilityStatus(false, message, diagnosticCode, missingPrivilege, permissionDenied);
    }
}
