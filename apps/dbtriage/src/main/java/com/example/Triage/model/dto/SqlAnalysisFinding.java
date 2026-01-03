package com.example.Triage.model.dto;

import com.example.Triage.model.enums.Severity;
import lombok.Builder;

@Builder
public record SqlAnalysisFinding(
                Severity severity,
                String category,
                String title,
                String description,
                String recommendation) {
}

