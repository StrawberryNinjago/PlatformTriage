package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class TopWarningSkill implements PlatformTriageSkill {

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.TOP_WARNING,
                "Surface the highest-priority warning signal and suggest next checks.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can show risk signals, but I need a deployment summary loaded first.",
                    List.of("No summary available."),
                    List.of("Load deployment summary first."),
                    List.of("Try: check namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        if (context.topWarning() == null) {
            return new PlatformTriageSkillResult(
                    "tool",
                    "No explicit warning-level signal is currently identified.",
                    List.of("Health: " + context.summary().health().overall()),
                    List.of("Keep monitoring pod restarts/events for drift."),
                    List.of(
                            "Need details on any remaining findings?",
                            "Ask for primary issue if there is a blocking failure."
                    ),
                    metadata().tool(),
                    true,
                    context.summary()
            );
        }

        List<String> keyFindings = new ArrayList<>();
        var warning = context.topWarning();
        keyFindings.add("Top warning: " + warning.code() + " - " + warning.title());
        keyFindings.add("Explanation: " + warning.explanation());
        if (warning.evidence() != null && !warning.evidence().isEmpty()) {
            warning.evidence().forEach(e -> keyFindings.add("Evidence: " + e.kind() + "=" + e.name()));
        }

        List<String> nextSteps = new ArrayList<>();
        if (warning.nextSteps() != null && !warning.nextSteps().isEmpty()) {
            nextSteps.addAll(warning.nextSteps());
        }
        if (nextSteps.isEmpty()) {
            nextSteps.add("Track this warning over next two summary runs.");
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Top warning surfaced for current namespace.",
                keyFindings,
                nextSteps,
                List.of("Want detailed recommendations for this warning?"),
                metadata().tool(),
                true,
                warning
        );
    }
}
