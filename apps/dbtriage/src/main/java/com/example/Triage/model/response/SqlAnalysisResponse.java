package com.example.Triage.model.response;

import com.example.Triage.model.dto.*;
import com.example.Triage.model.enums.SqlOperationType;
import lombok.Builder;

import java.util.List;

@Builder
public record SqlAnalysisResponse(
                SqlOperationType detectedOperation,
                String analyzedSql,
                String outcomeSummary,
                List<SqlAnalysisFinding> findings,
                IndexMatchResult indexAnalysis,
                ConstraintViolationRisk constraintRisks,
                CascadeAnalysisResult cascadeAnalysis,
                boolean isValid,
                String parseError) {
}

