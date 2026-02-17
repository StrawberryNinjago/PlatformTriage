package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.platformtriage.model.dto.PodInfo;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class ListPodsSkill implements PlatformTriageSkill {

    private static final int MAX_PODS_IN_RESPONSE = 25;

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.LIST_PODS,
                "List pods from the currently loaded deployment summary.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can list pods, but I need a deployment summary loaded first.",
                    List.of("No summary loaded."),
                    List.of("Load a namespace summary first."),
                    List.of("Try: check namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        List<PodInfo> pods = context.summary().objects() == null
                || context.summary().objects().pods() == null
                ? List.of()
                : context.summary().objects().pods();

        if (pods.isEmpty()) {
            return new PlatformTriageSkillResult(
                    "tool",
                    "No pods are available in the current summary.",
                    List.of("Namespace: " + context.summary().target().namespace(), "Pod count: 0"),
                    List.of("Confirm selector/release scope and reload summary."),
                    List.of("Want me to summarize deployment health instead?"),
                    metadata().tool(),
                    true,
                    pods
            );
        }

        boolean unhealthyOnly = isUnhealthyOnlyRequest(context.question());
        List<PodInfo> filtered = unhealthyOnly
                ? pods.stream().filter(this::isUnhealthy).toList()
                : pods;

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Namespace: " + context.summary().target().namespace());
        keyFindings.add("Pods returned: " + filtered.size() + " / " + pods.size());

        List<PodInfo> limited = filtered.stream()
                .limit(MAX_PODS_IN_RESPONSE)
                .toList();

        for (PodInfo pod : limited) {
            keyFindings.add(String.format(
                    "Pod: %s | phase=%s | ready=%s | restarts=%d",
                    pod.name(),
                    safe(pod.phase()),
                    pod.ready(),
                    pod.restarts()
            ));
        }

        if (filtered.size() > MAX_PODS_IN_RESPONSE) {
            keyFindings.add("... +" + (filtered.size() - MAX_PODS_IN_RESPONSE) + " more pods.");
        }

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Ask for primary issue to identify the top fix.");
        nextSteps.add("Ask for top risks to inspect warnings.");
        if (!unhealthyOnly) {
            nextSteps.add("Ask: show unhealthy pods only.");
        }

        return new PlatformTriageSkillResult(
                "tool",
                unhealthyOnly
                        ? "Here are unhealthy pods from the current summary."
                        : "Here are pods from the current summary.",
                keyFindings,
                nextSteps,
                List.of("Do you want details for a specific pod issue code?"),
                metadata().tool(),
                true,
                filtered
        );
    }

    private boolean isUnhealthyOnlyRequest(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("unhealthy")
                || normalized.contains("not ready")
                || normalized.contains("failed")
                || normalized.contains("crash")
                || normalized.contains("error pods");
    }

    private boolean isUnhealthy(PodInfo pod) {
        if (!pod.ready()) {
            return true;
        }
        String phase = pod.phase();
        return phase == null || !"running".equalsIgnoreCase(phase);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "<unknown>" : value;
    }
}
