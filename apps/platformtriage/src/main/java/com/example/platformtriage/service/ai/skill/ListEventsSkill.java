package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.dto.EventInfo;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class ListEventsSkill implements PlatformTriageSkill {

    private static final int MAX_EVENTS_IN_RESPONSE = 60;

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.LIST_EVENTS,
                "List Kubernetes events from the current deployment summary.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can list events, but I need a deployment summary loaded first.",
                    List.of("No summary loaded."),
                    List.of("Load a summary first."),
                    List.of("Try: check namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        List<EventInfo> events = context.summary().objects() == null
                || context.summary().objects().events() == null
                ? List.of()
                : context.summary().objects().events();

        if (events.isEmpty()) {
            return new PlatformTriageSkillResult(
                    "tool",
                    "No Kubernetes events were collected for this summary.",
                    List.of("Namespace: " + context.summary().target().namespace()),
                    List.of("Run a summary refresh to refresh event window."),
                    List.of("Need me to inspect a pod or finding for next steps?"),
                    metadata().tool(),
                    true,
                    events
            );
        }

        boolean warningOnly = isWarningsOnlyRequested(context.question(), context.parameter("warningsOnly"));
        List<EventInfo> filtered = warningOnly
                ? events.stream().filter(e -> "Warning".equalsIgnoreCase(e.type())).toList()
                : events;

        List<EventInfo> limited = filtered.stream().limit(context.safeLimitEvents()).toList();

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Namespace: " + context.summary().target().namespace());
        keyFindings.add("Requested limit: " + context.safeLimitEvents());
        keyFindings.add("Events returned: " + limited.size() + " / " + filtered.size());
        keyFindings.add("Filter: " + (warningOnly ? "warning only" : "all events"));

        if (!limited.isEmpty()) {
            for (EventInfo e : limited) {
                StringBuilder line = new StringBuilder();
                line.append(e.type()).append(" / ").append(e.reason());
                line.append(" — ").append(e.involvedObjectKind()).append(" ").append(e.involvedObjectName());
                if (StringUtils.hasText(e.timestamp())) {
                    line.append(" @ ").append(e.timestamp());
                }
                String message = safe(e.message());
                if (StringUtils.hasText(message)) {
                    line.append(" | ").append(message);
                }
                keyFindings.add(line.toString());
            }
        }

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Look for repeated warnings on the same reason before escalating.");
        nextSteps.add("If warning events exist, pair with pod logs and evidence for the same object.");
        if (warningOnly) {
            nextSteps.add("Run list events without warning-only if you want full context.");
        } else {
            nextSteps.add("Run warning-only events for a focused list.");
        }

        return new PlatformTriageSkillResult(
                "tool",
                warningOnly
                        ? "Showing warning events from this deployment summary."
                        : "Showing events from this deployment summary.",
                keyFindings,
                nextSteps,
                List.of("Need pod logs for one of these involved objects? Ask with pod name."),
                metadata().tool(),
                true,
                limited
        );
    }

    private boolean isWarningsOnlyRequested(String question, String param) {
        if ("true".equalsIgnoreCase(param)) {
            return true;
        }
        String normalized = StringUtils.hasText(question) ? question.toLowerCase(Locale.ROOT) : "";
        return normalized.contains("warning only")
                || normalized.contains("warnings only")
                || normalized.contains("warning events only")
                || normalized.contains("only warning events");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
