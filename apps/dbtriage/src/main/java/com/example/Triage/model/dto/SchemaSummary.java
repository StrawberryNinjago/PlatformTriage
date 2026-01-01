package com.example.Triage.model.dto;

import java.util.List;

public record SchemaSummary(
        int tableCount,
        List<TableExistence> importantTables) {
}
