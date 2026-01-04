package com.example.Triage.model.dto;

/**
 * Represents the availability status of a comparison section
 */
public record SectionAvailability(
        boolean available,
        boolean partial,
        String unavailabilityReason,
        String neededPrivilege,
        String impact) {

    public static SectionAvailability createAvailable() {
        return new SectionAvailability(true, false, null, null, null);
    }

    public static SectionAvailability createPartial(String reason) {
        return new SectionAvailability(true, true, reason, null, "Some drift results may be unknown");
    }

    public static SectionAvailability createUnavailable(String reason, String neededPrivilege, String impact) {
        return new SectionAvailability(false, false, reason, neededPrivilege, impact);
    }
}

