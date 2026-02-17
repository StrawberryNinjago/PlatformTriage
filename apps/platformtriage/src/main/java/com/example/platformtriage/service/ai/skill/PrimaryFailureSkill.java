package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.platformtriage.model.enums.OverallStatus;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PrimaryFailureSkill implements PlatformTriageSkill {

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.PRIMARY_FAILURE,
                "Explain the primary blocking finding and recommended first fix.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can identify a primary issue, but I need a loaded deployment summary.",
                    List.of("No summary loaded."),
                    List.of("Load a summary first using namespace and selector/release."),
                    List.of(
                            "Try: check namespace cart with selector app=cart-app",
                            "Then ask: what is the primary issue?"
                    ),
                    metadata().tool(),
                    false,
                    null
            );
        }

        var primary = context.primaryFailure();
        if (primary == null) {
            if (context.summary().health().overall() == OverallStatus.WARN) {
                return new PlatformTriageSkillResult(
                        "tool",
                        "No single primary failure is blocking. This namespace has warnings only.",
                        List.of("Health: WARN", "Primary blocking issue: none"),
                        List.of("Run risks to inspect warning details."),
                        List.of("Need evidence and next steps for a specific warning?"),
                        metadata().tool(),
                        true,
                        context.summary()
                );
            }
            if (context.summary().health().overall() == OverallStatus.PASS) {
                return new PlatformTriageSkillResult(
                        "tool",
                        "No primary failure detected. Deployment is currently passing.",
                        List.of("Health: PASS", "Blocking failures: none"),
                        List.of("If desired, check risks and evidence of degradation."),
                        List.of("Would you like a summary of evidence-driven risks?"),
                        metadata().tool(),
                        true,
                        context.summary()
                );
            }
            return new PlatformTriageSkillResult(
                    "tool",
                    "No primary failure identified in the current summary.",
                    List.of("Health: " + context.summary().health().overall()),
                    List.of("Run summarize for complete context."),
                    List.of("Run risks for warning-level details."),
                    metadata().tool(),
                    true,
                    context.summary()
            );
        }

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Primary issue: " + primary.code() + " - " + primary.title());
        keyFindings.add("Severity: " + primary.severity());
        keyFindings.add("Owner: " + primary.owner());
        keyFindings.add("Explanation: " + primary.explanation());

        if (primary.evidence() != null && !primary.evidence().isEmpty()) {
            keyFindings.add("Evidence count: " + primary.evidence().size());
            primary.evidence().forEach(e -> keyFindings.add("Evidence: " + e.kind() + "=" + e.name()
                    + (e.message() == null ? "" : " (" + e.message() + ")")));
        }

        List<String> nextSteps = new ArrayList<>();
        if (primary.nextSteps() != null && !primary.nextSteps().isEmpty()) {
            nextSteps.addAll(primary.nextSteps());
        } else {
            nextSteps.add("Review the owning workload and compare rollout events.");
            nextSteps.add("Collect logs and relevant describe output for the evidence objects.");
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Primary failure identified: " + primary.code() + ".",
                keyFindings,
                nextSteps,
                List.of("Want top warning/risk signals after this failure?", "Need remediation for a specific evidence object?"),
                metadata().tool(),
                true,
                context.summary()
        );
    }
}
