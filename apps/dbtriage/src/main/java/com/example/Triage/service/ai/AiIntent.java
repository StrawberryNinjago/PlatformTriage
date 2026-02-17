package com.example.Triage.service.ai;

import java.util.Collections;
import java.util.Map;

public record AiIntent(
        String tool,
        double confidence,
        Map<String, String> parameters
) {
    public static AiIntent chatFallback(String reason) {
        return new AiIntent("chat", 0.0d, Map.of("reason", reason));
    }

    public static AiIntent from(String tool, double confidence, Map<String, String> parameters) {
        return new AiIntent(tool, confidence, parameters == null ? Collections.emptyMap() : parameters);
    }

    public String tool() {
        return tool == null || tool.isBlank() ? "chat" : tool;
    }
}
