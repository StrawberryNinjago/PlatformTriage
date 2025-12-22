package com.example.Triage.model.response;

import java.util.List;

public record DbTableIntrospectResponse(
        String schema,
        String table,
        List<DbIndex> indexes,
        List<DbConstraint> constraints) {
    public record DbIndex(
            String name,
            boolean unique,
            boolean primary,
            String method,
            String definition) {
    }

    public record DbConstraint(
            String name,
            String type, // PRIMARY KEY, UNIQUE, FOREIGN KEY, CHECK
            List<String> columns, // best-effort for conkey-based constraints
            String definition) {
    }
}
