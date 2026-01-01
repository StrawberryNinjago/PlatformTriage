package com.example.Triage.model.dto;

import java.util.List;

public record DbConstraint(
                String table,
                String name,
                String type, // PRIMARY KEY, UNIQUE, FOREIGN KEY, CHECK
                List<String> columns, // best-effort for conkey-based constraints
                String definition) {
}
