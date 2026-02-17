package com.example.Triage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.Triage.handler.DbConnectionHandler;
import com.example.Triage.model.response.DbFlywayHealthResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FlywayHealthSkill implements DbTriageSkill {

    private final DbConnectionHandler connectionHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.FLYWAY_HEALTH,
                "Run migration health checks for the connected PostgreSQL instance.",
                true
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        DbFlywayHealthResponse health = connectionHandler.getFlywayHealth(context.connectionId());

        List<String> findings = new ArrayList<>();
        findings.add("Flyway status: " + health.status());

        if (health.flywaySummary() != null) {
            int appliedCount = health.flywaySummary().installedBySummary() == null
                    ? 0
                    : health.flywaySummary().installedBySummary().size();
            findings.add("Estimated applied migrations: " + appliedCount);
            findings.add("Failed migrations: " + health.flywaySummary().failedCount());
        }

        if (health.warnings() != null && !health.warnings().isEmpty()) {
            findings.add("Warnings: " + health.warnings().size());
        }

        return new DbTriageSkillResult(
                "tool",
                "Flyway health check executed.",
                findings,
                List.of(health.status() != null && health.status().name().contains("UNHEALTHY")
                        ? "Fix failed migrations before continuing application checks."
                        : "Next, run list tables to validate schema objects."),
                List.of("Want me to run table inspection next?"),
                metadata().tool(),
                true,
                health
        );
    }
}
