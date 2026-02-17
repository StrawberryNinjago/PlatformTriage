package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.OverallStatus;
import com.example.platformtriage.model.response.DeploymentSummaryResponse;
import com.example.platformtriage.service.DeploymentDoctorService;
import com.example.platformtriage.service.ai.AiIntent;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoadSummarySkill implements PlatformTriageSkill {

    private final DeploymentDoctorService service;

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.LOAD_SUMMARY,
                "Load deployment diagnostics from Kubernetes for namespace/selector/release.",
                false
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) throws Exception {
        String namespace = StringUtils.hasText(context.namespace()) ? context.namespace() : null;
        String selector = context.parameter("selector");
        String release = context.parameter("release");

        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release)) {
            selector = null;
        }

        if (!StringUtils.hasText(namespace)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can run a deployment diagnosis, but I still need the namespace.",
                    List.of("Missing namespace."),
                    List.of("Run: check app status in namespace cart"),
                    List.of(
                            "Which namespace should I query?",
                            "Do you also want to include a selector or release filter?"
                    ),
                    metadata().tool(),
                    false,
                    null
            );
        }

        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can query Kubernetes for namespace " + namespace + ", but I also need a selector or release.",
                    List.of("Missing selector/release parameter."),
                    List.of(
                            "Use selector app=<name> (for example app=cart-app) or set a release filter."
                    ),
                    List.of(
                            "Try: check namespace cart with selector app=cart-app",
                            "Try: check namespace cart with release cart"
                    ),
                    metadata().tool(),
                    false,
                    null
            );
        }

        DeploymentSummaryResponse summary = service.getSummary(namespace, selector, release, context.safeLimitEvents());

        List<String> findings = new ArrayList<>();
        findings.add("Overall status: " + summary.health().overall());
        findings.add("Deployments ready: " + summary.health().deploymentsReady());
        findings.add("Pod breakdown: "
                + "running=" + summary.health().pods().getOrDefault("running", 0) + ", "
                + "pending=" + summary.health().pods().getOrDefault("pending", 0) + ", "
                + "crashLoop=" + summary.health().pods().getOrDefault("crashLoop", 0));

        Finding primary = summary.primaryFailure();
        if (primary != null) {
            findings.add("Primary failure: " + primary.code() + " - " + primary.title());
        }
        if (summary.topWarning() != null) {
            findings.add("Top warning: " + summary.topWarning().code() + " - " + summary.topWarning().title());
        }

        List<String> nextSteps = new ArrayList<>();
        if (summary.health().overall() == OverallStatus.FAIL || summary.health().overall() == OverallStatus.UNKNOWN) {
            nextSteps.add("Run primary issue now to get root-cause actions.");
        } else if (!summary.findings().isEmpty()) {
            nextSteps.add("Run risks to inspect warning-level signals.");
        } else {
            nextSteps.add("No immediate follow-up needed; monitor key KPIs.");
        }

        List<String> openQuestions = new ArrayList<>();
        if (summary.health().overall() == OverallStatus.PASS) {
            openQuestions.add("Would you like me to summarize risk signals for this summary?");
        } else {
            openQuestions.add("Want me to identify the primary failure and first fix?");
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Deployment summary loaded for " + namespace + ".",
                findings,
                nextSteps,
                openQuestions,
                metadata().tool(),
                true,
                summary
        );
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
