package com.example.Triage.model.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record SchemaSummaryDto(
        int tableCount,
        List<TableExistence> importantTables) {
}
