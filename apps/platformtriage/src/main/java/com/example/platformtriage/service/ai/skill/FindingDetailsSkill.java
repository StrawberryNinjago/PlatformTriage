package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.FailureCode;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class FindingDetailsSkill implements PlatformTriageSkill {

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.FINDING_DETAILS,
                "Explain details and next actions for a specific finding code.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can explain specific findings, but I need a loaded deployment summary.",
                    List.of("No summary loaded."),
                    List.of("Load a summary first."),
                    List.of("Try: check namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String requestedCode = safe(context.parameter("findingCode"));
        if (!StringUtils.hasText(requestedCode)) {
            List<String> codes = context.summary().findings().stream()
                    .map(f -> f.code().name())
                    .sorted()
                    .toList();

            return new PlatformTriageSkillResult(
                    "clarify",
                    "Please include a failure code to explain, such as `CRASH_LOOP` or `IMAGE_PULL_FAILED`.",
                    codes.isEmpty() ? List.of("No findings in current summary.") : List.of("Available codes: " + String.join(", ", codes)),
                    List.of(
                            "Try: explain finding CRASH_LOOP",
                            "Try: explain finding image_pull_failed (case-insensitive)"
                    ),
                    List.of("If you want all findings, ask for summarize first."),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String normalizedCode = requestedCode.trim().toUpperCase(Locale.ROOT);
        if (!isKnownCode(normalizedCode)) {
            normalizedCode = normalizeAlnum(normalizedCode);
        }
        String finalCodeForMatch = normalizedCode;
        String finalCodeLower = finalCodeForMatch.toLowerCase(Locale.ROOT);

        Finding match = context.summary().findings().stream()
                .filter(f -> f.code().name().equalsIgnoreCase(finalCodeForMatch)
                        || f.title().toLowerCase(Locale.ROOT).contains(finalCodeLower))
                .findFirst()
                .orElse(null);

        if (match == null) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I could not match that finding in the current summary.",
                    List.of("Code/txt not found: " + requestedCode),
                    List.of("Use a code from the current findings list."),
                    List.of("Try: summarize", "Then ask me to explain one of the listed findings."),
                    metadata().tool(),
                    false,
                    context.summary()
            );
        }

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Finding: " + match.code() + " / " + match.title());
        keyFindings.add("Severity: " + match.severity());
        keyFindings.add("Owner: " + match.owner());
        keyFindings.add("Explanation: " + match.explanation());
        if (match.evidence() != null && !match.evidence().isEmpty()) {
            match.evidence().forEach(e -> keyFindings.add("Evidence: " + e.kind() + "=" + e.name()
                    + (StringUtils.hasText(e.message()) ? " (" + e.message() + ")" : "")));
        }

        List<String> nextSteps = new ArrayList<>();
        if (match.nextSteps() != null && !match.nextSteps().isEmpty()) {
            nextSteps.addAll(match.nextSteps());
        } else {
            nextSteps.add("Review logs and Kubernetes events for the related object.");
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Found details for: " + match.code(),
                keyFindings,
                nextSteps,
                List.of("Need a different finding explained next?"),
                metadata().tool(),
                true,
                match
        );
    }

    private boolean isKnownCode(String candidate) {
        for (FailureCode code : FailureCode.values()) {
            if (code.name().equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAlnum(String input) {
        return input == null ? "" : input.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
