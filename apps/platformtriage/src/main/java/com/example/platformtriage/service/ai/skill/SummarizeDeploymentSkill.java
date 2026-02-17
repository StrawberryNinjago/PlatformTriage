package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.OverallStatus;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class SummarizeDeploymentSkill implements PlatformTriageSkill {

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.SUMMARIZE,
                "Summarize the latest deployment summary and top risks.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can summarize deployment status, but I need a loaded summary first.",
                    List.of("No summary loaded."),
                    List.of("Load a namespace summary first."),
                    List.of(
                            "Try: check namespace cart with selector app=cart-app",
                            "Then ask: summarize"
                    ),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String namespace = context.summary().target().namespace();
        int findingCount = context.summary().findings() == null ? 0 : context.summary().findings().size();
        OverallStatus overall = context.summary().health().overall();

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Target: namespace=" + safe(namespace)
                + ", selector=" + safe(context.summary().target().selector())
                + (StringUtils.hasText(context.summary().target().release()) ? ", release=" + context.summary().target().release() : ""));
        keyFindings.add("Health: " + overall);
        keyFindings.add("Findings: " + findingCount);
        if (context.summary().primaryFailure() != null) {
            keyFindings.add("Primary: " + context.summary().primaryFailure().code() + " / " + context.summary().primaryFailure().title());
        }
        if (context.summary().topWarning() != null) {
            keyFindings.add("Top warning: " + context.summary().topWarning().code() + " / " + context.summary().topWarning().title());
        }
        if (context.summary().objects() != null) {
            if (context.summary().objects().pods() != null && !context.summary().objects().pods().isEmpty()) {
                keyFindings.add("Pods inspected: " + context.summary().objects().pods().size());
            }
            if (context.summary().objects().deployments() != null && !context.summary().objects().deployments().isEmpty()) {
                keyFindings.add("Deployments inspected: " + context.summary().objects().deployments().size());
            }
        }

        List<String> nextSteps = new ArrayList<>();
        if (overall == OverallStatus.FAIL || overall == OverallStatus.UNKNOWN) {
            nextSteps.add("Run primary issue to identify first fix priority.");
            if (context.summary().primaryFailure() != null) {
                nextSteps.add("Review recommended steps in primary finding.");
            }
        } else if (overall == OverallStatus.WARN) {
            nextSteps.add("Run risks to inspect the highest risk signal.");
        } else {
            nextSteps.add("No blocking failures found. Keep monitoring events and restart trends.");
            if (context.summary().findings() != null && !context.summary().findings().isEmpty()) {
                nextSteps.add("Validate WARN-level findings before release.");
            }
        }

        List<Finding> topFindings = context.summary().findings().stream().limit(3).toList();
        if (!topFindings.isEmpty()) {
            List<String> findingCodes = topFindings.stream()
                    .map(f -> f.code() + " (" + f.severity() + ")")
                    .toList();
            keyFindings.add("Top issues: " + String.join(", ", findingCodes));
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Deployment summary includes " + keyFindings.get(0) + ".",
                keyFindings,
                nextSteps,
                List.of("Want a deeper view of the primary finding?", "Want top warning details?"),
                metadata().tool(),
                true,
                context.summary()
        );
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "<none>";
    }
}
