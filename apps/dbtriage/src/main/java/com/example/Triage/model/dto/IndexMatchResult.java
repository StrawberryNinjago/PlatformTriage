package com.example.Triage.model.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record IndexMatchResult(
                String tableName,
                List<String> queryColumns,
                boolean hasCompositeIndex,
                boolean hasPartialCoverage,
                List<String> matchedIndexes,
                List<String> suggestedIndexes) {
}

