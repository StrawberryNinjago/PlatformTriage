package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.response.DeploymentTraceMatch;
import com.example.platformtriage.model.response.DeploymentTraceSearchResponse;
import com.example.platformtriage.service.DeploymentDoctorService;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class TraceSearchSkill implements PlatformTriageSkill {

    private final DeploymentDoctorService service;

    public TraceSearchSkill(DeploymentDoctorService service) {
        this.service = service;
    }

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.TRACE_SEARCH,
                "Search deployment logs by trace id, optionally scoped to pod and scope.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) throws Exception {
        String namespace = context.activeNamespace();
        if (!StringUtils.hasText(namespace)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can search trace IDs, but I need a namespace.",
                    List.of("No namespace available."),
                    List.of("Load summary first or provide namespace and selector/release."),
                    List.of("Try: search trace trace-123 in namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String traceId = StringUtils.hasText(context.parameter("traceId"))
                ? context.parameter("traceId")
                : context.parameter("trace");
        if (!StringUtils.hasText(traceId)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can run trace search, but I could not detect the trace id.",
                    List.of("No trace id provided."),
                    List.of("Try: find trace id=trace-abc123 in logs", "Try: search for trace 4d3f9a"),
                    List.of("Need logs with the exact trace/correlation id."),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String selector = context.parameter("selector");
        String release = context.parameter("release");
        String podName = context.parameter("podName");
        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release) && context.summary() != null
                && context.summary().target() != null) {
            selector = context.summary().target().selector();
            release = context.summary().target().release();
        }

        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release) && context.summary() == null) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can search by trace id, but I need selector or release for scoping.",
                    List.of("No selector or release provided."),
                    List.of("Use selector app=<name> with namespace."),
                    List.of("Try: search trace abc123 in namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        Integer lineLimit = parsePositiveInt(context.parameter("logLines"));
        DeploymentTraceSearchResponse traceResponse = service.findTraceInLogs(
                namespace,
                selector,
                release,
                podName,
                traceId,
                lineLimit
        );

        return buildTraceResult(context, traceResponse);
    }

    private PlatformTriageSkillResult buildTraceResult(
            PlatformTriageSkillContext context,
            DeploymentTraceSearchResponse traceResponse
    ) {
        if (traceResponse == null) {
            return new PlatformTriageSkillResult(
                    "tool_error",
                    "Trace search returned no data.",
                    List.of("No trace data was returned."),
                    List.of("Retry with a valid namespace and trace id."),
                    List.of("Try: search trace abc123 with namespace cart and selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Trace id: " + safe(traceResponse.traceId()));
        keyFindings.add("Namespace: " + traceResponse.namespace());
        keyFindings.add("Searched pods: " + traceResponse.searchedPods());
        keyFindings.add("Total matches: " + traceResponse.totalMatches());

        int matchCount = traceResponse.matches() == null ? 0 : traceResponse.matches().size();
        List<DeploymentTraceMatch> matches = traceResponse.matches() == null ? List.of() : traceResponse.matches();
        for (DeploymentTraceMatch match : matches) {
            List<String> lines = match.lines() == null ? List.of() : match.lines();
            keyFindings.add("Pod: " + match.podName() + " -> lines: " + lines.size());
            if (lines.isEmpty()) {
                continue;
            }
            int shown = 0;
            for (String line : lines) {
                if (shown++ >= 2) {
                    continue;
                }
                keyFindings.add("  - " + line);
            }
            if (lines.size() > 2) {
                keyFindings.add("  - ... +" + (lines.size() - 2) + " more lines");
            }
        }

        List<String> nextSteps = new ArrayList<>();
        if (traceResponse.totalMatches() == 0) {
            nextSteps.add("No trace entries found. Expand line limit or validate trace id format.");
        } else {
            nextSteps.add("Open logs for each matching pod to inspect full context around this trace id.");
            if (matchCount > 0) {
                nextSteps.add("Ask for pod logs with a specific pod name from the matches above.");
            }
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Trace search completed for " + safe(traceResponse.traceId()) + ".",
                keyFindings,
                nextSteps,
                List.of("Want me to fetch full logs for any matching pod?"),
                metadata().tool(),
                true,
                traceResponse
        );
    }

    private Integer parsePositiveInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "<unknown>";
    }
}
