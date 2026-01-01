package com.example.Triage.model.enums;

public enum DbSchema {
    PUBLIC,
    CUSTOM; // escape hatch

    public static DbSchema from(String raw) {
        if (raw == null || raw.isBlank())
            return PUBLIC;
        if ("public".equalsIgnoreCase(raw))
            return PUBLIC;
        return CUSTOM;
    }
}
