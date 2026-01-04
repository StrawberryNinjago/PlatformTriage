package com.example.Triage.handler;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.model.dto.*;
import com.example.Triage.model.enums.ComparisonMode;
import com.example.Triage.model.enums.DriftSeverity;
import com.example.Triage.model.request.EnvironmentComparisonRequest;
import com.example.Triage.model.response.EnvironmentComparisonResponse;
import com.example.Triage.service.db.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Handler for environment comparison (schema drift detection)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentComparisonHandler {

    private final DbConnectionRegistry connectionRegistry;
    private final EnvironmentCapabilityService capabilityService;
    private final SchemaDriftService driftService;
    private final DbFlywayService flywayService;
    private final BlastRadiusService blastRadiusService;
    private final PrivilegeAnalysisService privilegeAnalysisService;

    public EnvironmentComparisonResponse compareEnvironments(EnvironmentComparisonRequest request) {
        log.info("Starting environment comparison: {} -> {}",
                request.sourceEnvironmentName(), request.targetEnvironmentName());

        // Get connection contexts
        DbConnectContextDto sourceCtx = connectionRegistry.get(request.sourceConnectionId())
                .orElseThrow(() -> new ConnectionNotFoundException(
                        "Source connection not found: " + request.sourceConnectionId()));

        DbConnectContextDto targetCtx = connectionRegistry.get(request.targetConnectionId())
                .orElseThrow(() -> new ConnectionNotFoundException(
                        "Target connection not found: " + request.targetConnectionId()));

        // Step 1: Build capability matrices
        EnvironmentCapabilityMatrix sourceCapabilities = capabilityService.buildCapabilityMatrix(
                request.sourceEnvironmentName(),
                request.sourceConnectionId(),
                sourceCtx
        );

        EnvironmentCapabilityMatrix targetCapabilities = capabilityService.buildCapabilityMatrix(
                request.targetEnvironmentName(),
                request.targetConnectionId(),
                targetCtx
        );

        // Step 2: Determine comparison mode
        ComparisonMode mode = determineComparisonMode(sourceCapabilities, targetCapabilities);
        String modeBanner = generateModeBanner(mode, request.targetEnvironmentName());

        // Step 3: Run drift detection (section by section)
        List<DriftSection> driftSections = new ArrayList<>();

        // Get common tables for column/constraint/index comparison
        Set<String> commonTables = new HashSet<>();

        // Tables section
        DriftSection tablesSection = driftService.compareTablesSection(
                sourceCtx, targetCtx, sourceCapabilities, targetCapabilities, request.specificTables());
        driftSections.add(tablesSection);

        // Determine common tables using actual table lists (ensures columns/indexes compare even when table list matches)
        if (tablesSection.availability().available() && !tablesSection.availability().partial()) {
            Set<String> sourceTables = driftService.listTables(sourceCtx, request.specificTables());
            Set<String> targetTables = driftService.listTables(targetCtx, request.specificTables());
            commonTables.addAll(sourceTables);
            commonTables.retainAll(targetTables);
        }

        // Columns section
        if (!commonTables.isEmpty()) {
            DriftSection columnsSection = driftService.compareColumnsSection(
                    sourceCtx, targetCtx, sourceCapabilities, targetCapabilities, commonTables);
            driftSections.add(columnsSection);

            // Constraints section
            DriftSection constraintsSection = driftService.compareConstraintsSection(
                    sourceCtx, targetCtx, sourceCapabilities, targetCapabilities, commonTables);
            driftSections.add(constraintsSection);

            // Indexes section
            DriftSection indexesSection = driftService.compareIndexesSection(
                    sourceCtx, targetCtx, sourceCapabilities, targetCapabilities, commonTables);
            driftSections.add(indexesSection);
        }

        // Step 4: Flyway comparison
        FlywayComparisonDto flywayComparison = compareFlywayHistory(
                sourceCtx, targetCtx, sourceCapabilities, targetCapabilities);

        // Step 5: Analyze missing migrations
        FlywayMigrationGap flywayMigrationGap = analyzeMissingMigrations(
                sourceCtx, targetCtx, flywayComparison, sourceCapabilities, targetCapabilities);

        // Step 6: Generate blast radius analysis
        List<BlastRadiusItem> blastRadius = blastRadiusService.generateBlastRadius(driftSections);

        // Step 7: Analyze missing privileges
        List<PrivilegeRequirement> missingPrivileges = privilegeAnalysisService.analyzeMissingPrivileges(
                targetCapabilities);

        // Step 8: Generate privilege request snippet
        String privilegeRequestSnippet = privilegeAnalysisService.generatePrivilegeRequestSnippet(
                missingPrivileges, request.targetEnvironmentName());

        // Step 9: Generate diagnostic conclusions (evidence-based)
        List<DiagnosticConclusion> conclusions = generateConclusions(
                driftSections, flywayComparison, flywayMigrationGap, mode, blastRadius);

        // Step 10: Build identity strings
        String sourceIdentity = buildIdentityString(sourceCtx, request.sourceEnvironmentName());
        String targetIdentity = buildIdentityString(targetCtx, request.targetEnvironmentName());

        // Step 11: Calculate KPIs
        ComparisonKPIs kpis = calculateKPIs(driftSections, flywayMigrationGap);

        return new EnvironmentComparisonResponse(
                request.sourceEnvironmentName(),
                request.targetEnvironmentName(),
                sourceIdentity,
                targetIdentity,
                request.schema(),
                mode,
                modeBanner,
                kpis,
                sourceCapabilities,
                targetCapabilities,
                driftSections,
                flywayComparison,
                flywayMigrationGap,
                blastRadius,
                conclusions,
                missingPrivileges,
                privilegeRequestSnippet,
                Instant.now().toString()
        );
    }

    private ComparisonMode determineComparisonMode(
            EnvironmentCapabilityMatrix source,
            EnvironmentCapabilityMatrix target) {

        // Check if both can connect and read identity
        if (!source.connect().available() || !target.connect().available()) {
            return ComparisonMode.BLOCKED;
        }

        // Check if we have minimal metadata access
        boolean hasMinimalAccess = source.tables().available() && target.tables().available();

        if (!hasMinimalAccess) {
            return ComparisonMode.BLOCKED;
        }

        // Check if we have full access
        boolean hasFullAccess = source.columns().available() && target.columns().available()
                && source.constraints().available() && target.constraints().available()
                && source.indexes().available() && target.indexes().available();

        return hasFullAccess ? ComparisonMode.FULL : ComparisonMode.PARTIAL;
    }

    private String generateModeBanner(ComparisonMode mode, String targetEnv) {
        return switch (mode) {
            case FULL -> String.format(
                    "✅ Full Comparison: Full schema comparison available for both environments.");
            case PARTIAL -> String.format(
                    "⚠️ Partial Comparison: %s metadata access is limited. Some drift results may be unknown.", targetEnv);
            case BLOCKED -> String.format(
                    "❌ Blocked Comparison: %s connection lacks required metadata access.", targetEnv);
        };
    }

    private FlywayComparisonDto compareFlywayHistory(
            DbConnectContextDto sourceCtx,
            DbConnectContextDto targetCtx,
            EnvironmentCapabilityMatrix sourceCapabilities,
            EnvironmentCapabilityMatrix targetCapabilities) {

        if (!sourceCapabilities.flywayHistory().available() || !targetCapabilities.flywayHistory().available()) {
            return new FlywayComparisonDto(
                    false, null, null, null, null, false, null, null, null, null,
                    "Flyway history not accessible in one or both environments"
            );
        }

        try {
            var sourceFlywayResponse = flywayService.getFlywayHealth(sourceCtx);
            var targetFlywayResponse = flywayService.getFlywayHealth(targetCtx);
            
            FlywaySummaryDto sourceFlyway = sourceFlywayResponse.flywaySummary();
            FlywaySummaryDto targetFlyway = targetFlywayResponse.flywaySummary();

            String sourceVersion = sourceFlyway.latestApplied() != null
                    ? sourceFlyway.latestApplied().version() : null;
            String targetVersion = targetFlyway.latestApplied() != null
                    ? targetFlyway.latestApplied().version() : null;
            
            Integer sourceRank = sourceFlyway.latestApplied() != null
                    ? sourceFlyway.latestApplied().installedRank() : null;
            Integer targetRank = targetFlyway.latestApplied() != null
                    ? targetFlyway.latestApplied().installedRank() : null;

            boolean versionMatch = Objects.equals(sourceVersion, targetVersion);

            String message = versionMatch
                    ? "Flyway versions match"
                    : String.format("Flyway version mismatch: %s vs %s", sourceVersion, targetVersion);

            return new FlywayComparisonDto(
                    true,
                    sourceVersion,
                    targetVersion,
                    sourceRank,
                    targetRank,
                    versionMatch,
                    sourceFlyway.failedCount(),
                    targetFlyway.failedCount(),
                    sourceFlyway.latestApplied() != null ? sourceFlyway.latestApplied().installedBy() : null,
                    targetFlyway.latestApplied() != null ? targetFlyway.latestApplied().installedBy() : null,
                    message
            );
        } catch (Exception e) {
            log.error("Failed to compare Flyway history: {}", e.getMessage());
            return new FlywayComparisonDto(
                    false, null, null, null, null, false, null, null, null, null,
                    "Failed to read Flyway history: " + e.getMessage()
            );
        }
    }

    private FlywayMigrationGap analyzeMissingMigrations(
            DbConnectContextDto sourceCtx,
            DbConnectContextDto targetCtx,
            FlywayComparisonDto flywayComparison,
            EnvironmentCapabilityMatrix sourceCapabilities,
            EnvironmentCapabilityMatrix targetCapabilities) {

        if (!flywayComparison.available()) {
            return new FlywayMigrationGap(
                    false,
                    "Flyway history not accessible",
                    List.of(),
                    null,
                    null
            );
        }

        if (flywayComparison.versionMatch()) {
            return new FlywayMigrationGap(
                    true,
                    "Flyway versions match - no missing migrations",
                    List.of(),
                    flywayComparison.sourceLatestRank(),
                    flywayComparison.targetLatestRank()
            );
        }

        // If target is behind, try to identify missing migrations
        Integer sourceRank = flywayComparison.sourceLatestRank();
        Integer targetRank = flywayComparison.targetLatestRank();

        if (sourceRank != null && targetRank != null && sourceRank > targetRank) {
            try {
                // Get all migrations from source
                List<FlywayHistoryRowDto> allSourceMigrations = flywayService.getFlywayHistory(sourceCtx, sourceRank);
                
                // Filter to only those with rank > targetRank
                List<FlywayHistoryRowDto> missing = allSourceMigrations.stream()
                        .filter(m -> m.installedRank() != null && m.installedRank() > targetRank)
                        .filter(m -> m.success() != null && m.success())
                        .toList();

                return new FlywayMigrationGap(
                        true,
                        String.format("Target is missing %d migration(s)", missing.size()),
                        missing,
                        sourceRank,
                        targetRank
                );
            } catch (Exception e) {
                log.error("Failed to analyze missing migrations: {}", e.getMessage());
                return new FlywayMigrationGap(
                        false,
                        "Failed to read Flyway history details: " + e.getMessage(),
                        List.of(),
                        sourceRank,
                        targetRank
                );
            }
        }

        return new FlywayMigrationGap(
                false,
                "Cannot determine missing migrations - version ordering unclear",
                List.of(),
                sourceRank,
                targetRank
        );
    }

    private String buildIdentityString(DbConnectContextDto ctx, String envName) {
        return String.format("%s %s:%d/%s (%s)",
                envName,
                ctx.host(),
                ctx.port(),
                ctx.database(),
                ctx.username());
    }
    
    private ComparisonKPIs calculateKPIs(List<DriftSection> driftSections, FlywayMigrationGap flywayMigrationGap) {
        int compatibilityErrors = (int) driftSections.stream()
                .flatMap(s -> s.driftItems().stream())
                .filter(d -> "Compatibility".equals(d.category()) && d.severity() == DriftSeverity.ERROR)
                .count();
        
        int performanceWarnings = (int) driftSections.stream()
                .flatMap(s -> s.driftItems().stream())
                .filter(d -> "Performance".equals(d.category()))
                .count();
        
        int missingMigrations = flywayMigrationGap.detectable() && flywayMigrationGap.missingMigrations() != null 
                ? flywayMigrationGap.missingMigrations().size()
                : 0;
        
        boolean hasCriticalIssues = compatibilityErrors > 0 || missingMigrations > 0;
        
        return new ComparisonKPIs(compatibilityErrors, performanceWarnings, missingMigrations, hasCriticalIssues);
    }

    private List<DiagnosticConclusion> generateConclusions(
            List<DriftSection> driftSections,
            FlywayComparisonDto flywayComparison,
            FlywayMigrationGap flywayMigrationGap,
            ComparisonMode mode,
            List<BlastRadiusItem> blastRadius) {

        List<DiagnosticConclusion> conclusions = new ArrayList<>();

        // Count total errors and warnings
        long totalErrors = driftSections.stream()
                .flatMap(s -> s.driftItems().stream())
                .filter(d -> d.severity() == DriftSeverity.ERROR)
                .count();

        long totalWarnings = driftSections.stream()
                .flatMap(s -> s.driftItems().stream())
                .filter(d -> d.severity() == DriftSeverity.WARN)
                .count();

        long compatibilityErrors = driftSections.stream()
                .flatMap(s -> s.driftItems().stream())
                .filter(d -> d.category().equals("Compatibility") && d.severity() == DriftSeverity.ERROR)
                .count();

        long performanceIssues = driftSections.stream()
                .flatMap(s -> s.driftItems().stream())
                .filter(d -> d.category().equals("Performance"))
                .count();

        // Overall drift assessment with evidence
        if (totalErrors > 0) {
            List<String> evidence = new ArrayList<>();
            evidence.add(String.format("Total critical differences: %d", totalErrors));
            evidence.add(String.format("Compatibility issues: %d", compatibilityErrors));
            
            // Add specific examples
            driftSections.stream()
                    .flatMap(s -> s.driftItems().stream())
                    .filter(d -> d.severity() == DriftSeverity.ERROR)
                    .limit(3)
                    .forEach(d -> evidence.add(String.format("%s: %s", d.objectName(), d.message())));

            List<NextAction> actions = new ArrayList<>();
            actions.add(NextAction.builder().label("Show Drift Details").target("drift-sections").build());
            actions.add(NextAction.builder().label("Show Blast Radius").target("blast-radius").build());
            if (flywayMigrationGap.detectable() && !flywayMigrationGap.missingMigrations().isEmpty()) {
                actions.add(NextAction.builder().label("Show Missing Migrations").target("missing-migrations").build());
            }
            
            conclusions.add(new DiagnosticConclusion(
                    DriftSeverity.ERROR,
                    "Compatibility",
                    "Critical schema drift detected - application failures likely",
                    evidence,
                    "INSERT/UPDATE/SELECT operations will fail; application may crash or return errors",
                    "Review and apply missing migrations to align target schema",
                    actions
            ));
        } else if (totalWarnings > 0) {
            List<String> evidence = new ArrayList<>();
            evidence.add(String.format("Total warnings: %d", totalWarnings));
            evidence.add(String.format("Performance-related: %d", performanceIssues));

            conclusions.add(new DiagnosticConclusion(
                    DriftSeverity.WARN,
                    "Performance",
                    "Schema differences detected - performance inconsistencies likely",
                    evidence,
                    "Query performance may differ; some operations may be slower",
                    "Review index differences to ensure consistent performance",
                    List.of(
                            NextAction.builder().label("Show Index Drift").target("indexes-section").build(),
                            NextAction.builder().label("Show Blast Radius").target("blast-radius").build()
                    )
            ));
        } else if (mode == ComparisonMode.FULL) {
            conclusions.add(new DiagnosticConclusion(
                    DriftSeverity.INFO,
                    "Alignment",
                    "No schema drift detected - environments are aligned",
                    List.of("All tables match", "All columns match", "All indexes match"),
                    "No compatibility or performance risks detected",
                    "Continue monitoring for future changes",
                    List.of()
            ));
        }

        // Flyway-specific conclusion with evidence
        if (flywayComparison.available() && !flywayComparison.versionMatch()) {
            List<String> evidence = new ArrayList<>();
            evidence.add("Source latest version: " + flywayComparison.sourceLatestVersion());
            evidence.add("Target latest version: " + flywayComparison.targetLatestVersion());
            evidence.add("Source failed migrations: " + (flywayComparison.sourceFailedCount() != null ? flywayComparison.sourceFailedCount() : 0));
            evidence.add("Target failed migrations: " + (flywayComparison.targetFailedCount() != null ? flywayComparison.targetFailedCount() : 0));

            if (flywayMigrationGap.detectable() && !flywayMigrationGap.missingMigrations().isEmpty()) {
                evidence.add(String.format("Missing migrations: %d", flywayMigrationGap.missingMigrations().size()));
            }

            List<NextAction> flywayActions = new ArrayList<>();
            if (flywayMigrationGap.detectable() && !flywayMigrationGap.missingMigrations().isEmpty()) {
                flywayActions.add(NextAction.builder().label("Show Missing Migrations").target("missing-migrations").build());
            }
            flywayActions.add(NextAction.builder().label("Open Flyway Health (Target)").target("flyway-target").build());
            flywayActions.add(NextAction.builder().label("Copy Diagnostics").target("copy-diagnostics").build());
            
            conclusions.add(new DiagnosticConclusion(
                    DriftSeverity.ERROR,
                    "Migration",
                    "Flyway mismatch detected - target missing migrations",
                    evidence,
                    "Target likely missing migration(s) that introduced schema objects used by the app",
                    String.format("Apply all migrations up to version %s to target environment",
                            flywayComparison.sourceLatestVersion()),
                    flywayActions
            ));
        }

        // Index-specific conclusion with evidence
        Optional<DriftSection> indexSection = driftSections.stream()
                .filter(s -> s.sectionName().equals("Indexes"))
                .findFirst();

        if (indexSection.isPresent() && indexSection.get().differCount() > 0) {
            List<String> evidence = new ArrayList<>();
            evidence.add(String.format("Missing indexes: %d", indexSection.get().differCount()));
            
            // Add performance impact indicators
            long highRiskIndexes = indexSection.get().driftItems().stream()
                    .filter(d -> "High".equals(d.riskLevel()))
                    .count();
            
            if (highRiskIndexes > 0) {
                evidence.add(String.format("High-risk indexes: %d", highRiskIndexes));
            }

            conclusions.add(new DiagnosticConclusion(
                    DriftSeverity.WARN,
                    "Performance",
                    "Index drift detected - query performance at risk",
                    evidence,
                    "Slow queries, timeouts, and CPU spikes likely under load",
                    "Review and align indexes to ensure consistent query performance",
                    List.of(
                            NextAction.builder().label("Show Index Details").target("indexes-section").build(),
                            NextAction.builder().label("Show Performance Impact").target("blast-radius").build()
                    )
            ));
        }

        // Partial comparison warning with evidence
        if (mode == ComparisonMode.PARTIAL) {
            List<String> evidence = new ArrayList<>();
            driftSections.stream()
                    .filter(s -> !s.availability().available())
                    .forEach(s -> evidence.add(s.sectionName() + ": " + s.availability().unavailabilityReason()));

            conclusions.add(new DiagnosticConclusion(
                    DriftSeverity.WARN,
                    "Access",
                    "Comparison is partial due to limited metadata access",
                    evidence,
                    "Some drift may be undetectable with current privileges",
                    "Request read-only access to information_schema and pg_catalog for full comparison",
                    List.of(
                            NextAction.builder().label("Show Capability Matrix").target("capability-matrix").build(),
                            NextAction.builder().label("Copy Privilege Request").target("privilege-request").build()
                    )
            ));
        }

        return conclusions;
    }
}

