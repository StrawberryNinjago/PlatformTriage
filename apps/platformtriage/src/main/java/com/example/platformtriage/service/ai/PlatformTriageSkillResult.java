package com.example.platformtriage.service.ai;

import java.util.List;

import com.example.platformtriage.model.response.AiTriageResponse;

public record PlatformTriageSkillResult(
        String mode,
        String answer,
        List<String> keyFindings,
        List<String> nextSteps,
        List<String> openQuestions,
        String executedTool,
        boolean toolExecuted,
        Object toolResult
) {
    public AiTriageResponse toResponse() {
        return new AiTriageResponse(mode, answer, keyFindings, nextSteps, openQuestions, executedTool, toolExecuted, toolResult);
    }
}
