package com.example.platformtriage.service.ai;

import java.util.Collections;
import java.util.List;

public record PlatformTriageSkillMetadata(
        String tool,
        String description,
        boolean requiresSummaryContext,
        List<String> requiredParameters
) {
    public PlatformTriageSkillMetadata(String tool, String description, boolean requiresSummaryContext) {
        this(tool, description, requiresSummaryContext, Collections.emptyList());
    }
}
