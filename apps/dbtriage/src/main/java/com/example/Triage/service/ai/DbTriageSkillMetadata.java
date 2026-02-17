package com.example.Triage.service.ai;

import java.util.Collections;
import java.util.List;

public record DbTriageSkillMetadata(
        String tool,
        String description,
        boolean requiresConnection,
        List<String> requiredParameters
) {
    public DbTriageSkillMetadata(String tool, String description, boolean requiresConnection) {
        this(tool, description, requiresConnection, Collections.emptyList());
    }
}
