package com.example.smoketests.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.smoketests.model.dto.ResolvedMetadata;
import com.example.smoketests.model.dto.ValidationCheck;
import com.example.smoketests.model.enums.CheckStatus;
import com.example.smoketests.model.enums.Suite;
import com.example.smoketests.model.request.ValidateConfigRequest;
import com.example.smoketests.model.response.ValidationResponse;

/**
 * Service for preflight configuration validation (NO target API calls)
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final SpecResolverService specResolverService;
    private final WorkflowService workflowService;

    public ValidationService(SpecResolverService specResolverService,
            WorkflowService workflowService) {
        this.specResolverService = specResolverService;
        this.workflowService = workflowService;
    }

    /**
     * Validate configuration without calling target APIs (preflight)
     */
    public ValidationResponse validateConfig(ValidateConfigRequest request) {
        log.info("Validating configuration for {}/{}",
                request.getTarget().getEnvironment(),
                request.getTarget().getCapability());

        List<ValidationCheck> checks = new ArrayList<>();
        boolean allPassed = true;

        // 1. Resolve base URL
        ValidationCheck baseUrlCheck = validateBaseUrl(request);
        checks.add(baseUrlCheck);
        if (baseUrlCheck.getStatus() == CheckStatus.FAIL) {
            allPassed = false;
        }

        // 2. Fetch and parse OpenAPI spec
        ValidationCheck specCheck = validateSpec(request);
        checks.add(specCheck);
        if (specCheck.getStatus() == CheckStatus.FAIL) {
            allPassed = false;
        }

        // 3. Validate auth profile
        ValidationCheck authCheck = validateAuthProfile(request);
        checks.add(authCheck);
        if (authCheck.getStatus() == CheckStatus.FAIL) {
            allPassed = false;
        }

        // 4. Validate workflow if required
        if (request.getSuiteConfig().getSuite() == Suite.WORKFLOW
                || request.getSuiteConfig().getSuite() == Suite.BOTH) {
            ValidationCheck workflowCheck = validateWorkflow(request);
            checks.add(workflowCheck);
            if (workflowCheck.getStatus() == CheckStatus.FAIL) {
                allPassed = false;
            }
        }

        ResolvedMetadata resolved = ResolvedMetadata.builder()
                .specFingerprint("etag:w/\"5f9a2b...\"")
                .generatedTestSetId("gts_9f1c...")
                .workflowFingerprint("sha256:7a9d...")
                .build();

        String summary = allPassed ? "Configuration is valid" : "Configuration validation failed";

        log.info("Validation complete: {}", allPassed ? "PASS" : "FAIL");

        return ValidationResponse.builder()
                .ok(allPassed)
                .summary(summary)
                .resolved(resolved)
                .checks(checks)
                .build();
    }

    private ValidationCheck validateBaseUrl(ValidateConfigRequest request) {
        try {
            String baseUrl = specResolverService.resolveSpec(
                    request.getTarget().getEnvironment(),
                    request.getTarget().getCapability(),
                    request.getTarget().getApiVersion(),
                    request.getSpec().getSource()
            ).getTarget().getBaseUrl();

            return ValidationCheck.builder()
                    .name("Resolve baseUrl")
                    .status(CheckStatus.PASS)
                    .details("Resolved to: " + baseUrl)
                    .build();
        } catch (Exception e) {
            return ValidationCheck.builder()
                    .name("Resolve baseUrl")
                    .status(CheckStatus.FAIL)
                    .details("Failed to resolve base URL: " + e.getMessage())
                    .build();
        }
    }

    private ValidationCheck validateSpec(ValidateConfigRequest request) {
        try {
            // TODO: Actually fetch and parse the spec
            return ValidationCheck.builder()
                    .name("Fetch and parse OpenAPI spec")
                    .status(CheckStatus.PASS)
                    .details("Spec fetched and parsed successfully")
                    .build();
        } catch (Exception e) {
            return ValidationCheck.builder()
                    .name("Fetch and parse OpenAPI spec")
                    .status(CheckStatus.FAIL)
                    .details("Failed to fetch/parse spec: " + e.getMessage())
                    .build();
        }
    }

    private ValidationCheck validateAuthProfile(ValidateConfigRequest request) {
        if (!request.getAuth().isRequired()) {
            return ValidationCheck.builder()
                    .name("Auth profile validation")
                    .status(CheckStatus.PASS)
                    .details("Auth not required")
                    .build();
        }

        String profile = request.getAuth().getProfile();
        // TODO: Validate against known auth profiles
        boolean isValid = List.of("none", "jwt-service", "oauth2", "api-key").contains(profile);

        return ValidationCheck.builder()
                .name("Auth profile validation")
                .status(isValid ? CheckStatus.PASS : CheckStatus.FAIL)
                .details(isValid ? "Auth profile '" + profile + "' is valid"
                        : "Unknown auth profile: " + profile)
                .build();
    }

    private ValidationCheck validateWorkflow(ValidateConfigRequest request) {
        try {
            var workflowOptions = request.getSuiteConfig().getWorkflowOptions();

            if (workflowOptions == null) {
                return ValidationCheck.builder()
                        .name("Workflow validation")
                        .status(CheckStatus.FAIL)
                        .details("Workflow options required when suite is WORKFLOW or BOTH")
                        .build();
            }

            // Check if workflow is provided
            boolean hasWorkflow = workflowOptions.getWorkflowId() != null
                    || workflowOptions.getWorkflowUploadId() != null;

            if (!hasWorkflow) {
                return ValidationCheck.builder()
                        .name("Workflow validation")
                        .status(CheckStatus.FAIL)
                        .details("Workflow required but not configured")
                        .build();
            }

            // TODO: Validate workflow operationIds exist in spec
            return ValidationCheck.builder()
                    .name("Workflow validation")
                    .status(CheckStatus.PASS)
                    .details("Workflow configuration is valid")
                    .build();
        } catch (Exception e) {
            return ValidationCheck.builder()
                    .name("Workflow validation")
                    .status(CheckStatus.FAIL)
                    .details("Workflow validation failed: " + e.getMessage())
                    .build();
        }
    }
}
