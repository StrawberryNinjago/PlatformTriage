package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.response.DeploymentSummaryResponse;
import com.example.platformtriage.model.response.DeploymentVersionCheck;
import com.example.platformtriage.service.DeploymentDoctorService;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class VersionCheckSkill implements PlatformTriageSkill {

    private final DeploymentDoctorService service;

    public VersionCheckSkill(DeploymentDoctorService service) {
        this.service = service;
    }

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.CHECK_VERSIONS,
                "Check runtime docker image, database version and flyway version for a deployment scope.",
                false
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) throws Exception {
        String namespace = context.activeNamespace();
        if (!StringUtils.hasText(namespace)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can run a version check, but I need a namespace.",
                    List.of("Missing namespace."),
                    List.of("Try: check versions in namespace cart with selector app=cart-app"),
                    List.of("Start with a deployment summary or provide namespace and scope."),
                    metadata().tool(),
                    false,
                    null
            );
        }

        String selector = context.parameter("selector");
        String release = context.parameter("release");

        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release) && context.summary() != null) {
            if (context.summary().target() != null) {
                selector = context.summary().target().selector();
                release = context.summary().target().release();
            }
        }

        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release) && context.summary() != null) {
            DeploymentVersionCheck versionCheck = context.summary().versionCheck();
            return buildVersionResult(context, namespace, versionCheck);
        }

        if (!StringUtils.hasText(selector) && !StringUtils.hasText(release)) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can run a version check for this namespace, but I need selector or release.",
                    List.of("Missing selector/release parameter."),
                    List.of(
                            "Use selector app=<name> or release=<name>",
                            "Try: check versions in namespace cart with selector app=cart-app"
                    ),
                    List.of("Need version context only for one workload at a time."),
                    metadata().tool(),
                    false,
                    null
            );
        }

        DeploymentVersionCheck versionCheck = service.getVersionCheck(namespace, selector, release);
        return buildVersionResult(context, namespace, versionCheck);
    }

    private PlatformTriageSkillResult buildVersionResult(
            PlatformTriageSkillContext context,
            String namespace,
            DeploymentVersionCheck versionCheck
    ) {
        if (versionCheck == null) {
            return new PlatformTriageSkillResult(
                    "tool_error",
                    "Version check returned no data.",
                    List.of("Could not collect version check information."),
                    List.of("Retry with namespace and selector/release."),
                    List.of("Try: check versions for namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Scope: " + describeScope(context, namespace));

        keyFindings.add("Docker images: " + versionCheck.dockerImages().size());
        versionCheck.dockerImages().forEach(image -> keyFindings.add("  - " + image));
        keyFindings.add("Database version: " + safe(versionCheck.databaseVersion())
                + " (source: " + safe(versionCheck.databaseVersionSource()) + ")");
        keyFindings.add("Flyway version: " + safe(versionCheck.flywayVersion())
                + " (source: " + safe(versionCheck.flywayVersionSource()) + ")");
        keyFindings.add("Notes: " + safe(versionCheck.notes()));

        List<String> nextSteps = new ArrayList<>();
        if (!StringUtils.hasText(versionCheck.databaseVersion())) {
            nextSteps.add("Database version could not be read. Verify DB connectivity and credentials exposure.");
        }
        if (!StringUtils.hasText(versionCheck.flywayVersion())) {
            nextSteps.add("Flyway version could not be resolved from public.flyway_schema_history.");
        }
        if (nextSteps.isEmpty()) {
            nextSteps.add("Versions appear available. Use trace search or logs if you need request-level evidence.");
        }

        return new PlatformTriageSkillResult(
                "tool",
                "Deployment version check completed.",
                keyFindings,
                nextSteps,
                List.of("Need details for a specific pod log or trace id?"),
                metadata().tool(),
                true,
                versionCheck
        );
    }

    private String describeScope(PlatformTriageSkillContext context, String namespace) {
        DeploymentSummaryResponse summary = context.summary();
        if (summary != null && summary.target() != null) {
            StringBuilder builder = new StringBuilder("namespace=").append(namespace);
            String selector = summary.target().selector();
            String release = summary.target().release();
            if (StringUtils.hasText(selector)) {
                builder.append(", selector=").append(selector);
            }
            if (StringUtils.hasText(release)) {
                builder.append(", release=").append(release);
            }
            return builder.toString();
        }
        return "namespace=" + namespace;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "N/A";
    }
}
