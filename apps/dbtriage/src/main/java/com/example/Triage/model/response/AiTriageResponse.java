package com.example.Triage.model.response;

import java.util.List;

public record AiTriageResponse(
        String mode,
        String answer,
        List<String> keyFindings,
        List<String> nextSteps,
        List<String> openQuestions,
        String executedTool,
        boolean toolExecuted,
        Object toolResult
) {}
