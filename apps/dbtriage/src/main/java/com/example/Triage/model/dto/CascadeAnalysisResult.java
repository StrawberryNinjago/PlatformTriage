package com.example.Triage.model.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CascadeAnalysisResult(
                String tableName,
                int cascadingForeignKeys,
                List<String> affectedTables,
                boolean hasRecursiveCascade,
                String cascadeDepth) {
}

