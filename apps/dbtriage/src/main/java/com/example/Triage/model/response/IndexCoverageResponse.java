package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.enums.ValidationStatus;

public record IndexCoverageResponse(
                String schema,
                String table,
                List<String> columns,
                boolean requireUnique,
                ValidationStatus status,
                String message,
                List<String> matchedIndexes,
                List<String> remediationHints) {
}
