package com.example.Triage.model.response;

import java.util.List;

public record DbTableSearchResponse(
        String schema,
        String searchQuery,
        List<TableMatch> matches) {

    public record TableMatch(
            String tableName,
            Long estimatedRowCount,
            String owner) {
    }
}

