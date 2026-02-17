package com.example.Triage.service.ai;

import java.util.Collections;
import java.util.Map;

import org.springframework.util.StringUtils;

public record DbTriageSkillContext(
        String question,
        String connectionId,
        String schema,
        AiIntent intent,
        Map<String, String> parameters
) {

    public Map<String, String> parameters() {
        return parameters == null ? Collections.emptyMap() : parameters;
    }

    public String parameter(String key) {
        return parameters().getOrDefault(key, null);
    }

    public boolean hasConnection() {
        return StringUtils.hasText(connectionId);
    }
}
