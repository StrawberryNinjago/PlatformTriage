package com.example.Triage.service.ai.skill;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.Triage.handler.SqlAnalysisHandler;
import com.example.Triage.model.enums.SqlOperationType;
import com.example.Triage.model.request.SqlAnalysisRequest;
import com.example.Triage.model.response.SqlAnalysisResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SqlAnalysisSkill implements DbTriageSkill {

    private final SqlAnalysisHandler sqlAnalysisHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.SQL_ANALYSIS,
                "Analyze a SQL statement for syntax/logic and privilege-sensitive failures.",
                true,
                List.of("sql")
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        String sql = context.parameter("sql");
        if (!StringUtils.hasText(sql)) {
            return new DbTriageSkillResult(
                    "clarify",
                    "I can analyze SQL, but please share SQL text.",
                    List.of("Missing SQL text."),
                    List.of("Example: why this SQL does not work: SELECT * FROM cart_item;"),
                    List.of("Please paste the full SQL statement."),
                    metadata().tool(),
                    false,
                    null
            );
        }

        SqlOperationType operationType = detectOperationType(sql, context.parameter("operationType"));
        SqlAnalysisResponse analysis = sqlAnalysisHandler.analyzeSql(
                new SqlAnalysisRequest(sql, operationType, context.connectionId())
        );

        long errorCount = analysis.findings() == null ? 0L :
                analysis.findings().stream()
                        .filter(f -> "ERROR".equalsIgnoreCase(f.severity().toString()))
                        .count();
        long warningCount = analysis.findings() == null ? 0L :
                analysis.findings().stream()
                        .filter(f -> "WARN".equalsIgnoreCase(f.severity().toString()))
                        .count();

        return new DbTriageSkillResult(
                "tool",
                analysis.outcomeSummary(),
                List.of(
                        "Detected operation: " + analysis.detectedOperation(),
                        "Findings: " + errorCount + " errors, " + warningCount + " warnings"
                ),
                List.of("Review findings before executing SQL in production."),
                List.of("Want me to map this to affected table privileges too?"),
                metadata().tool(),
                true,
                analysis
        );
    }

    private SqlOperationType detectOperationType(String sql, String hint) {
        if (StringUtils.hasText(hint)) {
            try {
                return SqlOperationType.valueOf(hint.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                // fallback to SQL detection
            }
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("select")) {
            return SqlOperationType.SELECT;
        }
        if (normalized.startsWith("insert")) {
            return SqlOperationType.INSERT;
        }
        if (normalized.startsWith("update")) {
            return SqlOperationType.UPDATE;
        }
        if (normalized.startsWith("delete")) {
            return SqlOperationType.DELETE;
        }
        return SqlOperationType.UNKNOWN;
    }
}
