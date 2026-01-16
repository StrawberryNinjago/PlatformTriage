package com.example.smoketests.handler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.smoketests.model.response.RunResponse;
import com.example.smoketests.service.ExecutionService;

import lombok.RequiredArgsConstructor;

/**
 * Handler for creating diagnostic export bundles
 * Aggregates run data, adds recommendations, formats for sharing
 */
@Component
@RequiredArgsConstructor
public class ExportHandler {

    private static final Logger log = LoggerFactory.getLogger(ExportHandler.class);
    
    private final ExecutionService executionService;

    /**
     * Create export bundle for a smoke test run
     * Includes target, summary, results, recommendations
     */
    public Map<String, Object> createExportBundle(String runId) {
        log.info("Creating export bundle for run: {}", runId);
        
        // Get run data
        RunResponse run = executionService.getRunStatus(runId);
        
        // Build export bundle
        Map<String, Object> export = new HashMap<>();
        export.put("type", "smoke-test-diagnostics");
        export.put("runId", runId);
        export.put("generatedAt", Instant.now().toString());
        
        // Add target info
        export.put("target", buildTargetInfo(run));
        
        // Add resolved metadata
        if (run.getResolved() != null) {
            export.put("resolved", buildResolvedInfo(run));
        }
        
        // Add summary
        export.put("summary", buildSummaryInfo(run));
        
        // Add results
        if (run.getResults() != null && !run.getResults().isEmpty()) {
            export.put("results", run.getResults());
        }
        
        // Add recommendations
        export.put("recommendations", generateRecommendations(run));
        
        log.info("Export bundle created");
        return export;
    }

    /**
     * Build target information map
     */
    private Map<String, String> buildTargetInfo(RunResponse run) {
        // TODO: Get actual target info from run metadata
        Map<String, String> target = new HashMap<>();
        target.put("environment", "local");
        target.put("capability", "carts");
        target.put("apiVersion", "v1");
        target.put("baseUrl", "http://localhost:8081/carts/v1");
        return target;
    }

    /**
     * Build resolved metadata map
     */
    private Map<String, String> buildResolvedInfo(RunResponse run) {
        Map<String, String> resolved = new HashMap<>();
        if (run.getResolved() != null) {
            resolved.put("specFingerprint", run.getResolved().getSpecFingerprint());
            resolved.put("generatedTestSetId", run.getResolved().getGeneratedTestSetId());
            if (run.getResolved().getWorkflowId() != null) {
                resolved.put("workflowId", run.getResolved().getWorkflowId());
            }
        }
        return resolved;
    }

    /**
     * Build summary information map
     */
    private Map<String, Object> buildSummaryInfo(RunResponse run) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("status", run.getStatus().toString());
        
        if (run.getSummary() != null) {
            summary.put("topFinding", run.getSummary().getTopFinding());
            
            if (run.getSummary().getContract() != null) {
                summary.put("contractTests", Map.of(
                        "passed", run.getSummary().getContract().getPassed(),
                        "failed", run.getSummary().getContract().getFailed(),
                        "skipped", run.getSummary().getContract().getSkipped()
                ));
            }
            
            if (run.getSummary().getWorkflow() != null) {
                summary.put("workflowTests", Map.of(
                        "passed", run.getSummary().getWorkflow().getPassed(),
                        "failed", run.getSummary().getWorkflow().getFailed(),
                        "skipped", run.getSummary().getWorkflow().getSkipped(),
                        "cleanupAttempted", run.getSummary().getWorkflow().getCleanupAttempted()
                ));
            }
        }
        
        return summary;
    }

    /**
     * Generate actionable recommendations based on run results
     */
    private List<String> generateRecommendations(RunResponse run) {
        List<String> recommendations = new ArrayList<>();
        
        switch (run.getStatus()) {
            case PASSED:
                recommendations.add("All tests passed - no action required");
                recommendations.add("Consider running workflow smoke tests if only contract tests were run");
                break;
                
            case FAILED:
                recommendations.add("Review failed test results for root cause");
                recommendations.add("Check target environment availability and configuration");
                recommendations.add("Verify OpenAPI spec matches actual API behavior");
                if (run.getSummary() != null && run.getSummary().getWorkflow() != null) {
                    recommendations.add("If workflow failed, check cleanup was attempted to avoid orphaned data");
                }
                break;
                
            case PARTIAL:
                recommendations.add("Some tests failed - investigate partial failures");
                recommendations.add("Contract smoke may pass while workflow smoke fails - check workflow dependencies");
                recommendations.add("Review test configuration for edge cases");
                break;
                
            case RUNNING:
                recommendations.add("Test execution in progress - poll for completion");
                break;
        }
        
        // Add auth-related recommendations
        recommendations.add("If auth failures, verify token acquisition service is accessible");
        
        // Add spec-related recommendations
        recommendations.add("If schema validation fails, check if OpenAPI spec is up to date");
        
        return recommendations;
    }
}
