package com.example.Triage.model.response;

import java.util.List;

public record DbFindResponse(
        String schema,
        String nameContains,
        List<FoundIndex> indexes,
        List<FoundConstraint> constraints) {
    public record FoundIndex(
            String table,
            String name,
            boolean unique,
            boolean primary,
            String definition) {
    }

    public record FoundConstraint(
            String table,
            String name,
            String type,
            java.util.List<String> columns,
            String definition) {
    }
}
