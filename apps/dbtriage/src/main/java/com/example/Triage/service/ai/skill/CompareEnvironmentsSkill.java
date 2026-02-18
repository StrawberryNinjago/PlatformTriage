package com.example.Triage.service.ai.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.Triage.handler.EnvironmentComparisonHandler;
import com.example.Triage.model.dto.ComparisonKPIs;
import com.example.Triage.model.dto.DriftSection;
import com.example.Triage.model.enums.ComparisonMode;
import com.example.Triage.model.request.EnvironmentComparisonRequest;
import com.example.Triage.model.response.EnvironmentComparisonResponse;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;
import com.example.Triage.service.db.DbConnectionRegistry;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompareEnvironmentsSkill implements DbTriageSkill {

    private static final Pattern PORT_PATTERN = Pattern.compile("\\b(?:port\\s*[:=]?\\s*)?(\\d{4,5})\\b",
            Pattern.CASE_INSENSITIVE);
    private final EnvironmentComparisonHandler comparisonHandler;
    private final DbConnectionRegistry connectionRegistry;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.COMPARE_ENVIRONMENTS,
                "Compare two environments and report schema alignment.",
                false
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        CompareSelection selection = resolveSelection(context);
        if (selection == null) {
            return compareClarificationResult();
        }

        EnvironmentComparisonResponse response = comparisonHandler.compareEnvironments(
                new EnvironmentComparisonRequest(
                        selection.source.id(),
                        selection.target.id(),
                        safe(selection.sourceLabel),
                        safe(selection.targetLabel),
                        context.parameter("schema"),
                        parseSpecificTables(context.parameter("specificTables"))
                )
        );

        int totalDifferences = response.driftSections() == null
                ? 0
                : response.driftSections().stream().mapToInt(DriftSection::differCount).sum();
        ComparisonKPIs kpis = response.kpis();

        List<String> findings = buildFindings(response, totalDifferences);
        List<String> nextSteps = buildNextSteps(response, totalDifferences);
        List<String> openQuestions = buildOpenQuestions();

        String answer = buildAnswer(response.comparisonMode(), totalDifferences, kpis);

        return new DbTriageSkillResult(
                "tool",
                answer,
                findings,
                nextSteps,
                openQuestions,
                metadata().tool(),
                true,
                response
        );
    }

    private CompareSelection resolveSelection(DbTriageSkillContext context) {
        List<DbConnectContextDto> activeConnections = connectionRegistry
                .listActiveConnections()
                .stream()
                .sorted(Comparator.comparing(DbConnectContextDto::createdAt).reversed())
                .toList();

        if (activeConnections.isEmpty()) {
            return null;
        }

        String sourceConnectionId = trim(context.parameter("sourceConnectionId"));
        String targetConnectionId = trim(context.parameter("targetConnectionId"));
        DbConnectContextDto sourceConnection = selectById(activeConnections, sourceConnectionId);
        DbConnectContextDto targetConnection = selectById(activeConnections, targetConnectionId);

        if (sourceConnection != null && targetConnection == null) {
            if (!targetConnectionId.isBlank()) {
                return null;
            }
            targetConnection = selectLatestOther(activeConnections, sourceConnection.id());
        }

        if (sourceConnection == null && StringUtils.hasText(sourceConnectionId)) {
            return null;
        }

        if (sourceConnection == null) {
            sourceConnection = selectByPort(activeConnections, extractPortHints(context.question(), "first"), sourceConnection);
            targetConnection = selectByPort(activeConnections, extractPortHints(context.question(), "second"), targetConnection);
            if (sourceConnection == null && targetConnection != null) {
                sourceConnection = selectByContextConnection(activeConnections, context.connectionId());
                if (sourceConnection == null || sourceConnection.id().equals(targetConnection.id())) {
                    sourceConnection = selectLatestOther(activeConnections, targetConnection.id());
                }
            }
        }

        if (sourceConnection != null && targetConnection == null && StringUtils.hasText(targetConnectionId)) {
            return null;
        }

        if (sourceConnection != null && targetConnection != null && sourceConnection.id().equals(targetConnection.id())) {
            return null;
        }

        if (sourceConnection == null && targetConnection == null) {
            sourceConnection = selectByContextConnection(activeConnections, context.connectionId());
            targetConnection = selectLatestOther(activeConnections, sourceConnection != null ? sourceConnection.id() : null);

            if (sourceConnection == null && activeConnections.size() >= 2) {
                sourceConnection = activeConnections.get(0);
                targetConnection = activeConnections.get(1);
            }
        }

        if (sourceConnection == null || targetConnection == null) {
            return null;
        }

        String sourceLabel = trim(context.parameter("sourceEnvironmentName"));
        String targetLabel = trim(context.parameter("targetEnvironmentName"));
        if (!StringUtils.hasText(sourceLabel)) {
            sourceLabel = buildEnvironmentLabel(sourceConnection);
        }
        if (!StringUtils.hasText(targetLabel)) {
            targetLabel = buildEnvironmentLabel(targetConnection);
        }

        return new CompareSelection(sourceConnection, targetConnection, sourceLabel, targetLabel);
    }

    private DbConnectContextDto selectByContextConnection(List<DbConnectContextDto> activeConnections, String contextConnectionId) {
        if (!StringUtils.hasText(contextConnectionId)) {
            return null;
        }
        return selectById(activeConnections, contextConnectionId);
    }

    private DbConnectContextDto selectById(List<DbConnectContextDto> activeConnections, String connectionId) {
        if (!StringUtils.hasText(connectionId)) {
            return null;
        }
        return activeConnections.stream()
                .filter(conn -> conn.id().equals(connectionId))
                .findFirst()
                .orElse(null);
    }

    private DbConnectContextDto selectLatestOther(List<DbConnectContextDto> activeConnections, String excludedId) {
        return activeConnections.stream()
                .filter(conn -> excludedId == null || !conn.id().equals(excludedId))
                .findFirst()
                .orElse(null);
    }

    private DbConnectContextDto selectByPort(
            List<DbConnectContextDto> activeConnections,
            Integer port,
            DbConnectContextDto existingSelection) {
        if (port == null) {
            return existingSelection;
        }
        if (existingSelection != null && existingSelection.port() == port) {
            return existingSelection;
        }
        return activeConnections.stream()
                .filter(conn -> conn.port() == port)
                .findFirst()
                .orElse(null);
    }

    private Integer extractPortHints(String question, String ordinal) {
        List<Integer> ports = new ArrayList<>();
        var matcher = PORT_PATTERN.matcher(question == null ? "" : question);
        while (matcher.find()) {
            try {
                ports.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed port references
            }
        }

        if (ports.isEmpty()) {
            return null;
        }

        if ("first".equalsIgnoreCase(ordinal) && ports.size() >= 1) {
            return ports.get(0);
        }

        if ("second".equalsIgnoreCase(ordinal) && ports.size() >= 2) {
            return ports.get(1);
        }

        return null;
    }

    private List<String> parseSpecificTables(String rawTables) {
        if (!StringUtils.hasText(rawTables)) {
            return null;
        }

        String[] entries = rawTables.split(",");
        List<String> tables = Arrays.stream(entries)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .toList();

        return tables.isEmpty() ? null : tables;
    }

    private List<String> buildFindings(EnvironmentComparisonResponse response, int totalDifferences) {
        List<String> findings = new ArrayList<>();
        findings.add("Source: " + response.sourceIdentity());
        findings.add("Target: " + response.targetIdentity());
        findings.add("Mode: " + response.comparisonMode());
        findings.add("Schema differences: " + totalDifferences);
        if (response.kpis() != null) {
            findings.add("Compatibility errors: " + response.kpis().compatibilityErrors());
            findings.add("Missing migrations: " + response.kpis().missingMigrations());
        }
        findings.add(response.modeBanner());
        return findings;
    }

    private List<String> buildNextSteps(EnvironmentComparisonResponse response, int totalDifferences) {
        List<String> nextSteps = new ArrayList<>();
        if (totalDifferences > 0) {
            nextSteps.add("Review drift sections to resolve schema mismatches first.");
            nextSteps.add("Align migrations on the target before promoting code.");
        } else {
            nextSteps.add("Re-run comparison after your next migration batch.");
        }

        if (response.comparisonMode() == ComparisonMode.PARTIAL) {
            nextSteps.add("Grant required read privileges and rerun for a full comparison.");
        }

        return nextSteps;
    }

    private List<String> buildOpenQuestions() {
        return List.of(
                "Need to compare a specific subset of tables?",
                "Want to run comparison for a named schema?",
                "If you want explicit pair selection, provide connection IDs or ports."
        );
    }

    private String buildAnswer(ComparisonMode mode, int totalDifferences, ComparisonKPIs kpis) {
        if (mode == ComparisonMode.BLOCKED) {
            return "Environment alignment check ran, but access is blocked.";
        }

        if (mode == ComparisonMode.PARTIAL) {
            return "Alignment looks similar on checked metadata, but comparison is partial due access limits.";
        }

        if (totalDifferences > 0 || (kpis != null && kpis.hasCriticalIssues())) {
            return "Environments are not fully aligned. Review drift differences before proceeding.";
        }

        return "Environments are aligned for the checked scope.";
    }

    private DbTriageSkillResult compareClarificationResult() {
        return new DbTriageSkillResult(
                "clarify",
                "I can compare environments, but I need two active connections.",
                List.of("Need two connections (source and target)"),
                List.of("Connect to both environments first, then rerun this request."),
                List.of("You can also provide source and target connection IDs directly."),
                metadata().tool(),
                false,
                null
        );
    }

    private String buildEnvironmentLabel(DbConnectContextDto connectionContext) {
        return String.format(
                "%s:%d/%s@%s",
                connectionContext.host(),
                connectionContext.port(),
                connectionContext.database(),
                connectionContext.username()
        );
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record CompareSelection(
            DbConnectContextDto source,
            DbConnectContextDto target,
            String sourceLabel,
            String targetLabel
    ) {
    }
}
