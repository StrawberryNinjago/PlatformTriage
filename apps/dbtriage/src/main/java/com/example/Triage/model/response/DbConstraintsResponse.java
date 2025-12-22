package com.example.Triage.model.response;

public record DbConstraintsResponse(
        String schema,
        String table,
        java.util.List<DbConstraint> constraints) {
    public record DbConstraint(
            String name,
            String type, // PRIMARY KEY, UNIQUE, FOREIGN KEY, CHECK
            java.util.List<String> columns, // best-effort; FK columns are included too
            String definition // pg_get_constraintdef output
    ) {
    }
}
