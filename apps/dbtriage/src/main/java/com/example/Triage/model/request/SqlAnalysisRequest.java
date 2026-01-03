package com.example.Triage.model.request;

import com.example.Triage.model.enums.SqlOperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SqlAnalysisRequest(
                @NotBlank @Size(max = 50000) String sql,
                SqlOperationType operationType,
                @NotBlank String connectionId) {
}

