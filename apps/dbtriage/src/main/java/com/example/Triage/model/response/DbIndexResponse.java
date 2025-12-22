package com.example.Triage.model.response;

import java.util.List;

public record DbIndexResponse(
                String schema,
                String table,
                List<DbIndex> indexes) {
        public record DbIndex(
                        String name,
                        boolean unique,
                        boolean primary,
                        String method,
                        String definition) {
        }
}