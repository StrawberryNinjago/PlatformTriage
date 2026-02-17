package com.example.platformtriage.service.ai;

import java.util.Collections;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.example.platformtriage.model.response.DeploymentSummaryResponse;
import com.example.platformtriage.model.dto.Finding;

public record PlatformTriageSkillContext(
        String question,
        String namespace,
        String selector,
        String release,
        Integer limitEvents,
        DeploymentSummaryResponse summary,
        AiIntent intent,
        Map<String, String> parameters
) {

    public Map<String, String> parameters() {
        return parameters == null ? Collections.emptyMap() : parameters;
    }

    public String parameter(String key) {
        return parameters().getOrDefault(key, null);
    }

    public boolean hasSummary() {
        return summary != null;
    }

    public String activeNamespace() {
        return StringUtils.hasText(namespace) ? namespace : "";
    }

    public int safeLimitEvents() {
        return limitEvents == null || limitEvents <= 0 ? 50 : limitEvents;
    }

    public Finding primaryFailure() {
        return summary != null ? summary.primaryFailure() : null;
    }

    public Finding topWarning() {
        return summary != null ? summary.topWarning() : null;
    }
}
