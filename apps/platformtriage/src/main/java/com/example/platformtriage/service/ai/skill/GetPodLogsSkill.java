package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.dto.PodInfo;
import com.example.platformtriage.service.DeploymentDoctorService;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class GetPodLogsSkill implements PlatformTriageSkill {

    private static final int MAX_LOG_LINES = 300;

    private final DeploymentDoctorService deploymentDoctorService;

    public GetPodLogsSkill(DeploymentDoctorService deploymentDoctorService) {
        this.deploymentDoctorService = deploymentDoctorService;
    }

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.GET_POD_LOGS,
                "Fetch recent logs from a matching pod (optionally with a line limit).",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can fetch pod logs, but I need a deployment summary loaded first.",
                    List.of("No summary loaded."),
                    List.of("Load a deployment summary first."),
                    List.of(
                            "Try: check namespace cart with selector app=cart-app",
                            "Then ask: show last 20 lines of logs for cart-app-pod"
                    ),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String namespace = context.summary().target().namespace();
        List<PodInfo> pods = context.summary().objects() == null
                || context.summary().objects().pods() == null
                        ? List.of()
                        : context.summary().objects().pods();

        String podName = resolvePodName(context.parameter("podName"), context.question(), pods);
        if (!StringUtils.hasText(podName)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "Please tell me which pod name you want logs from.",
                    List.of(
                            "No pod specified.",
                            "Known pods: " + (pods.isEmpty()
                                    ? "none"
                                    : String.join(", ", pods.stream().map(PodInfo::name).toList()))
                    ),
                    List.of("Try: show last 10 lines of logs for pod <name>."),
                    List.of("Ask me with a specific pod name from the previous 'show my pods' output."),
                    metadata().tool(),
                    false,
                    null
            );
        }

        int requestedLines = parseLineLimit(context.parameter("logLines"));
        try {
            String logs = deploymentDoctorService.getPodLogs(namespace, podName, requestedLines);
            List<String> rawLines = logs == null ? List.of() : logs.lines().toList();
            List<String> keyFindings = buildLogFindings(namespace, podName, requestedLines, rawLines);
            return new PlatformTriageSkillResult(
                    "tool",
                    "Fetched " + Math.min(requestedLines, rawLines.size()) + " log lines for " + podName + ".",
                    keyFindings,
                    List.of(
                            "Review warnings/errors in log lines.",
                            "Ask for another pod if needed.",
                            "Ask for a wider log window if you need more context."
                    ),
                    List.of("Want health or primary issue details for this pod?"),
                    metadata().tool(),
                    true,
                    rawLines
            );
        } catch (Exception e) {
            return new PlatformTriageSkillResult(
                    "tool_error",
                    "Failed to fetch pod logs: " + e.getMessage(),
                    List.of("Could not fetch logs for pod " + podName + " in namespace " + namespace),
                    List.of(
                            "Verify pod name and namespace.",
                            "Confirm RBAC permissions for reading pod logs."
                    ),
                    List.of("Try: show my pods"),
                    metadata().tool(),
                    false,
                    null
            );
        }
    }

    private int parseLineLimit(String rawLimit) {
        if (!StringUtils.hasText(rawLimit)) {
            return 10;
        }
        try {
            int parsed = Integer.parseInt(rawLimit.trim());
            if (parsed <= 0) {
                return 10;
            }
            return Math.min(parsed, MAX_LOG_LINES);
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private String resolvePodName(String requestedPodName, String question, List<PodInfo> pods) {
        if (StringUtils.hasText(requestedPodName)) {
            for (PodInfo pod : pods) {
                if (pod.name().equalsIgnoreCase(requestedPodName)) {
                    return pod.name();
                }
            }
            return null;
        }

        String lowerQuestion = StringUtils.hasText(question) ? question.toLowerCase() : "";
        for (PodInfo pod : pods) {
            if (StringUtils.hasText(pod.name()) && lowerQuestion.contains(pod.name().toLowerCase())) {
                return pod.name();
            }
        }

        return pickDefaultPod(pods);
    }

    private String pickDefaultPod(List<PodInfo> pods) {
        if (pods.isEmpty()) {
            return null;
        }

        PodInfo firstUnready = pods.stream()
                .filter(p -> !p.ready())
                .min(Comparator.comparing(PodInfo::name))
                .orElse(null);
        if (firstUnready != null) {
            return firstUnready.name();
        }

        PodInfo highestRestarts = pods.stream()
                .max(Comparator.comparingInt(PodInfo::restarts))
                .orElse(pods.get(0));
        return highestRestarts.name();
    }

    private List<String> buildLogFindings(
            String namespace,
            String podName,
            int requestedLines,
            List<String> rawLines
    ) {
        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Namespace: " + namespace);
        keyFindings.add("Pod: " + podName);
        keyFindings.add("Requested lines: " + requestedLines);
        keyFindings.add("Returned lines: " + rawLines.size());

        if (rawLines.isEmpty()) {
            keyFindings.add("No logs returned.");
            return keyFindings;
        }

        int maxToShow = Math.min(25, rawLines.size());
        for (int i = 0; i < maxToShow; i++) {
            String line = rawLines.get(i);
            keyFindings.add((i + 1) + ". " + (line == null || line.isBlank() ? "<blank>" : line));
        }
        if (rawLines.size() > maxToShow) {
            keyFindings.add("... (+ " + (rawLines.size() - maxToShow) + " more lines hidden)");
        }
        return keyFindings;
    }
}
