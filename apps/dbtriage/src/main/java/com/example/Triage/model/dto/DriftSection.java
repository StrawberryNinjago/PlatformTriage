package com.example.Triage.model.dto;

import java.util.List;

/**
 * Represents a section of drift results (e.g., Tables, Columns, Indexes)
 */
public record DriftSection(
        String sectionName,
        String description,
        SectionAvailability availability,
        List<DriftItem> driftItems,
        int matchCount,
        int differCount,
        int unknownCount) {
}

