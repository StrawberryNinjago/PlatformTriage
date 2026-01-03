package com.example.Triage.service.db;

import com.example.Triage.model.dto.*;
import com.example.Triage.model.enums.Severity;
import com.example.Triage.model.enums.SqlOperationType;
import com.example.Triage.model.response.SqlAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SqlAnalysisService {

    private final SqlParserService sqlParserService;
    private final SqlIndexAnalysisService indexAnalysisService;
    private final SqlConstraintAnalysisService constraintAnalysisService;
    private final SqlCascadeAnalysisService cascadeAnalysisService;

    // Root entity tables that typically have many cascading relationships
    private static final Set<String> ROOT_ENTITIES = Set.of(
            "cart", "order", "account", "user", "customer", "organization", 
            "company", "tenant", "project", "workspace"
    );

    public SqlAnalysisResponse analyzeSql(
            DbConnectContextDto ctx,
            String sql,
            SqlOperationType suggestedType) throws SQLException {

        // Parse the SQL
        SqlParserService.ParsedSql parsed = sqlParserService.parseSql(sql);

        if (!parsed.isValid()) {
            return SqlAnalysisResponse.builder()
                    .detectedOperation(SqlOperationType.UNKNOWN)
                    .analyzedSql(sql)
                    .isValid(false)
                    .parseError(parsed.getErrorMessage())
                    .findings(List.of())
                    .build();
        }

        // Use suggested type if provided, otherwise use detected
        SqlOperationType opType = suggestedType != null ? suggestedType : parsed.getOperationType();

        // Determine schema (default to public if not specified)
        String schema = "public";
        String tableName = parsed.getTableName();

        List<SqlAnalysisFinding> findings = new ArrayList<>();
        IndexMatchResult indexAnalysis = null;
        ConstraintViolationRisk constraintRisks = null;
        CascadeAnalysisResult cascadeAnalysis = null;

        try {
            switch (opType) {
                case SELECT -> {
                    indexAnalysis = analyzeSelectStatement(ctx, schema, parsed, findings);
                }
                case INSERT -> {
                    indexAnalysis = null; // INSERTs don't need index analysis for WHERE
                    constraintRisks = analyzeInsertStatement(ctx, schema, parsed, findings);
                }
                case UPDATE -> {
                    indexAnalysis = analyzeUpdateStatement(ctx, schema, parsed, findings);
                    constraintRisks = analyzeUpdateConstraints(ctx, schema, parsed, findings);
                }
                case DELETE -> {
                    indexAnalysis = analyzeDeleteStatement(ctx, schema, parsed, findings);
                    cascadeAnalysis = analyzeDeleteCascade(ctx, schema, parsed, findings);
                }
            }
        } catch (Exception e) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.ERROR)
                    .category("Analysis Error")
                    .title("Failed to analyze SQL")
                    .description("Error during analysis: " + e.getMessage())
                    .recommendation("Verify table exists and connection is valid")
                    .build());
        }

        // A. Sort findings by severity: ERROR, WARN, INFO
        List<SqlAnalysisFinding> sortedFindings = sortFindingsBySeverity(findings);

        // B. Generate outcome summary
        String outcomeSummary = generateOutcomeSummary(opType, sortedFindings, 
                indexAnalysis, constraintRisks, cascadeAnalysis);

        return SqlAnalysisResponse.builder()
                .detectedOperation(opType)
                .analyzedSql(sql)
                .outcomeSummary(outcomeSummary)
                .findings(sortedFindings)
                .indexAnalysis(indexAnalysis)
                .constraintRisks(constraintRisks)
                .cascadeAnalysis(cascadeAnalysis)
                .isValid(true)
                .parseError(null)
                .build();
    }

    private List<SqlAnalysisFinding> sortFindingsBySeverity(List<SqlAnalysisFinding> findings) {
        Map<Severity, Integer> severityOrder = Map.of(
                Severity.ERROR, 1,
                Severity.WARN, 2,
                Severity.INFO, 3
        );

        return findings.stream()
                .sorted(Comparator.comparingInt(f -> severityOrder.getOrDefault(f.severity(), 999)))
                .collect(Collectors.toList());
    }

    private String generateOutcomeSummary(
            SqlOperationType opType,
            List<SqlAnalysisFinding> findings,
            IndexMatchResult indexAnalysis,
            ConstraintViolationRisk constraintRisks,
            CascadeAnalysisResult cascadeAnalysis) {

        long errorCount = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnCount = findings.stream().filter(f -> f.severity() == Severity.WARN).count();

        return switch (opType) {
            case SELECT -> {
                if (errorCount > 0) {
                    yield "❌ Query has issues that may cause failures or poor performance.";
                } else if (warnCount > 0) {
                    yield "⚠️ Query will work but may have suboptimal performance.";
                } else if (indexAnalysis != null && indexAnalysis.hasCompositeIndex()) {
                    yield "✅ Query is well indexed and should perform efficiently.";
                } else {
                    yield "✅ Query structure is valid.";
                }
            }
            case INSERT -> {
                if (errorCount > 0) {
                    yield "❌ INSERT will fail due to missing NOT NULL columns.";
                } else if (warnCount > 0) {
                    yield "⚠️ INSERT may fail due to constraint violations depending on values.";
                } else {
                    yield "✅ INSERT statement structure is valid.";
                }
            }
            case UPDATE -> {
                if (errorCount > 0) {
                    yield "❌ UPDATE has dangerous patterns that should be reviewed.";
                } else if (warnCount > 0) {
                    yield "⚠️ UPDATE may affect multiple rows or trigger constraint checks.";
                } else {
                    yield "✅ UPDATE statement appears safe.";
                }
            }
            case DELETE -> {
                if (cascadeAnalysis != null && cascadeAnalysis.cascadingForeignKeys() > 0) {
                    int count = cascadeAnalysis.cascadingForeignKeys();
                    yield String.format("⚠️ DELETE will cascade to %d related table%s.",
                            count, count == 1 ? "" : "s");
                } else if (errorCount > 0) {
                    yield "❌ DELETE has dangerous patterns that must be fixed.";
                } else {
                    yield "✅ DELETE statement appears safe.";
                }
            }
            default -> "Analysis complete.";
        };
    }

    private IndexMatchResult analyzeSelectStatement(
            DbConnectContextDto ctx,
            String schema,
            SqlParserService.ParsedSql parsed,
            List<SqlAnalysisFinding> findings) throws SQLException {

        IndexMatchResult indexResult = indexAnalysisService.analyzeIndexCoverage(
                ctx, schema, parsed.getTableName(), parsed.getWhereColumns());

        if (!parsed.getWhereColumns().isEmpty()) {
            if (!indexResult.hasCompositeIndex()) {
                if (indexResult.hasPartialCoverage()) {
                    findings.add(SqlAnalysisFinding.builder()
                            .severity(Severity.WARN)
                            .category("Index Coverage")
                            .title("Partial Index Coverage")
                            .description("Only partial index coverage found. Query may be slower than optimal.")
                            .recommendation("Consider creating composite index: " + 
                                    String.join(", ", indexResult.suggestedIndexes()))
                            .build());
                } else if (indexResult.matchedIndexes().isEmpty()) {
                    findings.add(SqlAnalysisFinding.builder()
                            .severity(Severity.ERROR)
                            .category("Index Coverage")
                            .title("No Index Found")
                            .description("No indexes found for WHERE clause columns: " + 
                                    String.join(", ", parsed.getWhereColumns()))
                            .recommendation("Create index: " + String.join(", ", indexResult.suggestedIndexes()))
                            .build());
                } else {
                    findings.add(SqlAnalysisFinding.builder()
                            .severity(Severity.INFO)
                            .category("Index Coverage")
                            .title("Index Coverage Found")
                            .description("Existing indexes cover the WHERE clause")
                            .recommendation("No action needed")
                            .build());
                }
            } else {
                findings.add(SqlAnalysisFinding.builder()
                        .severity(Severity.INFO)
                        .category("Index Coverage")
                        .title("Optimal Index Coverage")
                        .description("Composite index exists for all WHERE columns")
                        .recommendation("Query should perform well")
                        .build());
            }
        }

        return indexResult;
    }

    private ConstraintViolationRisk analyzeInsertStatement(
            DbConnectContextDto ctx,
            String schema,
            SqlParserService.ParsedSql parsed,
            List<SqlAnalysisFinding> findings) throws SQLException {

        ConstraintViolationRisk risks = constraintAnalysisService.analyzeInsertRisks(
                ctx, schema, parsed.getTableName(), parsed.getColumns());

        if (!risks.missingNotNullColumns().isEmpty()) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.ERROR)
                    .category("Constraint Violation")
                    .title("Missing NOT NULL Columns")
                    .description("Required columns not provided: " + 
                            String.join(", ", risks.missingNotNullColumns()))
                    .recommendation("Include all NOT NULL columns in INSERT statement")
                    .build());
        }

        if (!risks.uniqueConstraintColumns().isEmpty()) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.WARN)
                    .category("Constraint Validation")
                    .title("Potential UNIQUE Constraint Conflict")
                    .description("Columns with UNIQUE constraints: " + 
                            String.join(", ", risks.uniqueConstraintColumns()))
                    .recommendation("Ensure values are unique or handle conflicts with ON CONFLICT")
                    .build());
        }

        if (!risks.foreignKeyViolations().isEmpty()) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.WARN)
                    .category("Foreign Key")
                    .title("Foreign Key Columns Present")
                    .description("Foreign key columns: " + String.join(", ", risks.foreignKeyViolations()))
                    .recommendation("Ensure referenced records exist before INSERT")
                    .build());
        }

        return risks;
    }

    private IndexMatchResult analyzeUpdateStatement(
            DbConnectContextDto ctx,
            String schema,
            SqlParserService.ParsedSql parsed,
            List<SqlAnalysisFinding> findings) throws SQLException {

        if (!parsed.getWhereColumns().isEmpty()) {
            IndexMatchResult indexResult = indexAnalysisService.analyzeIndexCoverage(
                    ctx, schema, parsed.getTableName(), parsed.getWhereColumns());

            if (!indexResult.hasCompositeIndex() && !indexResult.matchedIndexes().isEmpty()) {
                findings.add(SqlAnalysisFinding.builder()
                        .severity(Severity.WARN)
                        .category("Update Performance")
                        .title("UPDATE May Affect Multiple Rows")
                        .description("WHERE clause may match multiple rows without optimal index")
                        .recommendation("Review WHERE clause specificity")
                        .build());
            }

            return indexResult;
        }

        findings.add(SqlAnalysisFinding.builder()
                .severity(Severity.ERROR)
                .category("Dangerous Operation")
                .title("UPDATE Without WHERE Clause")
                .description("This will update ALL rows in the table")
                .recommendation("Add WHERE clause to limit scope")
                .build());

        return null;
    }

    private ConstraintViolationRisk analyzeUpdateConstraints(
            DbConnectContextDto ctx,
            String schema,
            SqlParserService.ParsedSql parsed,
            List<SqlAnalysisFinding> findings) throws SQLException {

        ConstraintViolationRisk risks = constraintAnalysisService.analyzeUpdateRisks(
                ctx, schema, parsed.getTableName(), parsed.getColumns());

        if (!risks.uniqueConstraintColumns().isEmpty()) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.WARN)
                    .category("Constraint Validation")
                    .title("Potential UNIQUE Constraint Conflict")
                    .description("Columns with UNIQUE constraints being updated: " + 
                            String.join(", ", risks.uniqueConstraintColumns()))
                    .recommendation("Ensure new values maintain uniqueness")
                    .build());
        }

        if (!risks.foreignKeyViolations().isEmpty()) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.WARN)
                    .category("Foreign Key")
                    .title("Updating Foreign Key Columns")
                    .description("Foreign key columns being updated: " + 
                            String.join(", ", risks.foreignKeyViolations()))
                    .recommendation("Ensure new values reference valid records")
                    .build());
        }

        return risks;
    }

    private IndexMatchResult analyzeDeleteStatement(
            DbConnectContextDto ctx,
            String schema,
            SqlParserService.ParsedSql parsed,
            List<SqlAnalysisFinding> findings) throws SQLException {

        if (parsed.getWhereColumns().isEmpty()) {
            findings.add(SqlAnalysisFinding.builder()
                    .severity(Severity.ERROR)
                    .category("Dangerous Operation")
                    .title("DELETE Without WHERE Clause")
                    .description("This will DELETE ALL rows in the table")
                    .recommendation("Add WHERE clause to limit scope")
                    .build());
            return null;
        }

        return indexAnalysisService.analyzeIndexCoverage(
                ctx, schema, parsed.getTableName(), parsed.getWhereColumns());
    }

    private CascadeAnalysisResult analyzeDeleteCascade(
            DbConnectContextDto ctx,
            String schema,
            SqlParserService.ParsedSql parsed,
            List<SqlAnalysisFinding> findings) throws SQLException {

        CascadeAnalysisResult cascade = cascadeAnalysisService.analyzeCascadeImpact(
                ctx, schema, parsed.getTableName());

        if (cascade.cascadingForeignKeys() > 0) {
            Severity severity = cascade.hasRecursiveCascade() || cascade.cascadingForeignKeys() >= 5 
                    ? Severity.ERROR : Severity.WARN;

            findings.add(SqlAnalysisFinding.builder()
                    .severity(severity)
                    .category("Cascade Delete")
                    .title("Cascading Delete Detected")
                    .description(String.format("DELETE will cascade to %d related table(s): %s",
                            cascade.cascadingForeignKeys(),
                            String.join(", ", cascade.affectedTables())))
                    .recommendation(cascade.hasRecursiveCascade() 
                            ? "Recursive cascade detected! Review carefully before executing."
                            : "Review cascade impact before executing DELETE")
                    .build());

            // D. Highlight dangerous defaults - root entity detection
            String tableName = parsed.getTableName().toLowerCase();
            if (isRootEntity(tableName) && cascade.cascadingForeignKeys() >= 2) {
                findings.add(SqlAnalysisFinding.builder()
                        .severity(Severity.ERROR)
                        .category("Dangerous Operation")
                        .title("DELETE Targets Root Entity")
                        .description(String.format(
                                "This DELETE targets a root entity (%s) with ON DELETE CASCADE. " +
                                "Cascades may remove large portions of related data across %d table(s).",
                                parsed.getTableName(), cascade.cascadingForeignKeys()))
                        .recommendation("Consider soft-delete pattern or manual cleanup for root entities. " +
                                "Verify this is intentional and document the blast radius.")
                        .build());
            }
        }

        return cascade;
    }

    private boolean isRootEntity(String tableName) {
        return ROOT_ENTITIES.contains(tableName.toLowerCase());
    }
}

