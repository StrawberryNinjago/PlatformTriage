package com.example.Triage.model.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ConstraintViolationRisk(
                String tableName,
                List<String> missingNotNullColumns,
                List<String> uniqueConstraintColumns,
                List<String> foreignKeyViolations,
                boolean hasRisks) {
}

