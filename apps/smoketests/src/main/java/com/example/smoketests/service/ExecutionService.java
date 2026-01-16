package com.example.smoketests.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.smoketests.model.dto.ResolvedMetadata;
import com.example.smoketests.model.dto.RunSummary;
import com.example.smoketests.model.dto.SuiteSummary;
import com.example.smoketests.model.enums.RunStatus;
import com.example.smoketests.model.request.RunSmokeTestsRequest;
import com.example.smoketests.model.response.RunResponse;

/**
 * Service for executing smoke tests and managing test runs
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    
    // In-memory storage of runs (TODO: Replace with database)
    private final Map<String, RunResponse> runs = new ConcurrentHashMap<>();

    /**
     * Start async smoke test run
     * Returns run ID immediately
     */
    public String startRun(RunSmokeTestsRequest request) {
        String runId = generateRunId();
        
        log.info("Starting smoke test run: {} for {}/{}",
                runId,
                request.getTarget().getEnvironment(),
                request.getTarget().getCapability());
        
        // Create initial run response with RUNNING status
        RunResponse initialRun = RunResponse.builder()
                .runId(runId)
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        
        runs.put(runId, initialRun);
        
        // TODO: Execute tests asynchronously
        // For now, just simulate with a delay and complete immediately
        executeTestsAsync(runId, request);
        
        log.info("Run started: {}", runId);
        return runId;
    }

    /**
     * Get run status and results
     */
    public RunResponse getRunStatus(String runId) {
        log.info("Fetching run status: {}", runId);
        
        RunResponse run = runs.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        
        return run;
    }

    /**
     * Execute tests asynchronously
     */
    private void executeTestsAsync(String runId, RunSmokeTestsRequest request) {
        // TODO: Use @Async or ExecutorService for real async execution
        // For now, simulate immediate completion with mock data
        
        RunResponse completedRun = RunResponse.builder()
                .runId(runId)
                .status(RunStatus.PASSED)
                .startedAt(Instant.now().minusSeconds(12))
                .finishedAt(Instant.now())
                .resolved(ResolvedMetadata.builder()
                        .specFingerprint("etag:w/\"5f9a2b...\"")
                        .generatedTestSetId("gts_9f1c...")
                        .workflowId(request.getSuiteConfig().getWorkflowOptions() != null ?
                                request.getSuiteConfig().getWorkflowOptions().getWorkflowId() : null)
                        .build())
                .summary(RunSummary.builder()
                        .contract(SuiteSummary.builder()
                                .passed(8)
                                .failed(0)
                                .skipped(0)
                                .durationMs(4200L)
                                .build())
                        .workflow(SuiteSummary.builder()
                                .passed(4)
                                .failed(0)
                                .skipped(0)
                                .cleanupAttempted(true)
                                .durationMs(6100L)
                                .build())
                        .topFinding("All tests passed")
                        .build())
                .results(List.of())
                .links(Map.of(
                        "export", "/api/smoke/runs/" + runId + "/export"
                ))
                .build();
        
        runs.put(runId, completedRun);
    }

    /**
     * Generate unique run ID
     */
    private String generateRunId() {
        return "run_" + System.currentTimeMillis();
    }
}
