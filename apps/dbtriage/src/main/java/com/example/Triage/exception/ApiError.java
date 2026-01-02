package com.example.Triage.exception;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.example.Triage.model.dto.DiagnosticItem;
import com.example.Triage.model.dto.NextAction;
import com.example.Triage.model.enums.Severity;

import lombok.Builder;

@Builder
public record ApiError(
        String code,
        String title,
        String message,
        Severity severity,
        int httpStatus,
        OffsetDateTime timestamp,
        String correlationId,
        Map<String, Object> context,
        List<DiagnosticItem> diagnostics,
        List<String> hints,
        List<NextAction> nextActions,
        boolean retryable) {
}