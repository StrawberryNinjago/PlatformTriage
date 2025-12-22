package com.example.Triage.model.response;

import java.util.List;

public record DbTablesResponse(
        String schema,
        List<TableInfo> tables) {

    public record TableInfo(
            String name,
            Long estimatedRowCount,
            String owner) {
    }
}

