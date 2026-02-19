package com.example.smoketests.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.smoketests.model.enums.ErrorCode;
import com.example.smoketests.model.request.GenerateTestsPreviewRequest;
import com.example.smoketests.model.request.RunSmokeTestsRequest;
import com.example.smoketests.model.request.ValidateConfigRequest;
import com.example.smoketests.model.response.ApiErrorResponse;
import com.example.smoketests.model.response.GenerateTestsPreviewResponse;
import com.example.smoketests.model.response.RunResponse;
import com.example.smoketests.model.response.SpecResolveResponse;
import com.example.smoketests.model.response.UploadResponse;
import com.example.smoketests.model.response.ValidationResponse;
import com.example.smoketests.model.response.WorkflowCatalogResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Main controller for Smoke Tests API Provides endpoints for: - Spec resolution
 * and fingerprinting - Workflow catalog access - File uploads (specs/workflows)
 * - Configuration validation (preflight) - Test execution and run management -
 * Evidence and export
 */
@RestController
@RequestMapping("/api/smoke")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SmokeTestsController {

    private static final Logger log = LoggerFactory.getLogger(SmokeTestsController.class);

    private final com.example.smoketests.service.SpecResolverService specResolverService;
    private final com.example.smoketests.service.WorkflowService workflowService;
    private final com.example.smoketests.service.ValidationService validationService;
    private final com.example.smoketests.service.ExecutionService executionService;
    private final com.example.smoketests.service.SmokeTestGenerationService smokeTestGenerationService;
    private final com.example.smoketests.service.UploadService uploadService;
    private final com.example.smoketests.handler.EvidenceHandler evidenceHandler;
    private final com.example.smoketests.handler.ExportHandler exportHandler;

    /**
     * 2.1 Resolve spec + fingerprint + cache status GET
     * /api/smoke/spec/resolve?environment=...&capability=...&apiVersion=...&source=BLOB&blobPath=...
     */
    @GetMapping("/spec/resolve")
    public ResponseEntity<SpecResolveResponse> resolveSpec(
            @RequestParam String environment,
            @RequestParam String capability,
            @RequestParam(required = false) String apiVersion,
            @RequestParam String source,
            @RequestParam(required = false) String blobPath,
            @RequestParam(required = false) String uploadId) {

        log.info("Resolving spec for {}/{}/{} from {}", environment, capability, apiVersion, source);

        com.example.smoketests.model.dto.SpecSource specSource = com.example.smoketests.model.dto.SpecSource.builder()
                .type(com.example.smoketests.model.enums.SpecSourceType.valueOf(source))
                .blobPath(blobPath)
                .uploadId(uploadId)
                .build();

        SpecResolveResponse response = specResolverService.resolveSpec(
                environment, capability, apiVersion, specSource);

        return ResponseEntity.ok(response);
    }

    /**
     * 2.2 Upload files (spec or workflow YAML) POST /api/uploads
     */
    @PostMapping("/uploads")
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {

        log.info("Uploading file: {} for purpose: {}", file.getOriginalFilename(), purpose);

        UploadResponse response = uploadService.uploadFile(file, purpose);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2.3 Workflow catalog GET /api/smoke/workflows?capability=cart
     */
    @GetMapping("/workflows")
    public ResponseEntity<WorkflowCatalogResponse> getWorkflowCatalog(
            @RequestParam String capability) {

        log.info("Fetching workflow catalog for capability: {}", capability);

        WorkflowCatalogResponse response = workflowService.getWorkflowCatalog(capability);
        return ResponseEntity.ok(response);
    }

    /**
     * 3.1 Validate config without executing target APIs (Preflight) POST
     * /api/smoke/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateConfig(
            @Valid @RequestBody ValidateConfigRequest request) {

        log.info("Validating configuration for {}/{}",
                request.getTarget().getEnvironment(),
                request.getTarget().getCapability());

        ValidationResponse response = validationService.validateConfig(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate contract smoke tests from Swagger/OpenAPI text and return ordered
     * preview.
     */
    @PostMapping("/spec/generate-tests")
    public ResponseEntity<GenerateTestsPreviewResponse> generateTestsPreview(
            @Valid @RequestBody GenerateTestsPreviewRequest request) {
        log.info("Generating smoke test preview (enforceOrder={})", request.isEnforceOrder());
        GenerateTestsPreviewResponse response = smokeTestGenerationService.generatePreview(
                request.getSpecContent(),
                request.isEnforceOrder());
        return ResponseEntity.ok(response);
    }

    /**
     * 5.1 Start a run (async) POST /api/smoke/runs
     */
    @PostMapping("/runs")
    public ResponseEntity<Map<String, Object>> startRun(
            @Valid @RequestBody RunSmokeTestsRequest request) {

        log.info("Starting smoke test run for {}/{}",
                request.getTarget().getEnvironment(),
                request.getTarget().getCapability());

        String runId = executionService.startRun(request);

        Map<String, Object> response = Map.of(
                "runId", runId,
                "status", "RUNNING",
                "links", Map.of(
                        "poll", "/api/smoke/runs/" + runId,
                        "export", "/api/smoke/runs/" + runId + "/export"
                )
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 5.2 Poll run status / results GET /api/smoke/runs/{runId}
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunResponse> getRunStatus(@PathVariable String runId) {
        log.info("Fetching run status: {}", runId);

        RunResponse response = executionService.getRunStatus(runId);
        return ResponseEntity.ok(response);
    }

    /**
     * 5.3 Evidence endpoint (for expandable row details) GET
     * /api/smoke/runs/{runId}/evidence/{evidenceRef}
     */
    @GetMapping("/runs/{runId}/evidence/{evidenceRef}")
    public ResponseEntity<com.example.smoketests.model.dto.Evidence> getEvidence(
            @PathVariable String runId,
            @PathVariable String evidenceRef) {

        log.info("Fetching evidence: {} for run: {}", evidenceRef, runId);

        com.example.smoketests.model.dto.Evidence evidence = evidenceHandler.getEvidence(runId, evidenceRef);
        return ResponseEntity.ok(evidence);
    }

    /**
     * 6) Export diagnostics GET /api/smoke/runs/{runId}/export
     */
    @GetMapping("/runs/{runId}/export")
    public ResponseEntity<Map<String, Object>> exportDiagnostics(@PathVariable String runId) {

        log.info("Exporting diagnostics for run: {}", runId);

        Map<String, Object> export = exportHandler.createExportBundle(runId);
        return ResponseEntity.ok(export);
    }

    /**
     * Exception handler for all controller errors
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        log.error("Exception in SmokeTestsController: {}", e.getMessage(), e);

        ApiErrorResponse.ErrorInfo errorInfo = ApiErrorResponse.ErrorInfo.builder()
                .code(ErrorCode.VALIDATION_FAILED)
                .message(e.getMessage() != null ? e.getMessage() : "An unexpected error occurred")
                .details(Map.of("detail", e.getClass().getSimpleName()))
                .build();

        ApiErrorResponse response = ApiErrorResponse.builder()
                .error(errorInfo)
                .build();

        HttpStatus status = (e instanceof IllegalArgumentException)
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status).body(response);
    }

}
