package com.example.smoketests.handler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.smoketests.model.dto.Evidence;

/**
 * Handler for evidence retrieval and formatting Sanitizes request/response data
 * (redacts secrets, truncates bodies)
 */
@Component
public class EvidenceHandler {

    private static final Logger log = LoggerFactory.getLogger(EvidenceHandler.class);
    private static final int MAX_BODY_PREVIEW_LENGTH = 500;

    /**
     * Get evidence for a specific test execution Retrieves and sanitizes
     * request/response evidence
     */
    public Evidence getEvidence(String runId, String evidenceRef) {
        log.info("Retrieving evidence: {} for run: {}", evidenceRef, runId);

        // TODO: Retrieve actual evidence from storage
        // For now, return mock sanitized evidence
        Evidence evidence = buildMockEvidence(evidenceRef);

        log.info("Evidence retrieved and sanitized");
        return evidence;
    }

    /**
     * Build mock evidence (placeholder for real implementation)
     */
    private Evidence buildMockEvidence(String evidenceRef) {
        return Evidence.builder()
                .request(buildRequestInfo())
                .response(buildResponseInfo())
                .trace(buildTraceInfo())
                .build();
    }

    /**
     * Build request info with sanitized headers
     */
    private Evidence.RequestInfo buildRequestInfo() {
        Map<String, String> sanitizedHeaders = Map.of(
                "authorization", redactSecret("Bearer abc123..."),
                "content-type", "application/json"
        );

        return Evidence.RequestInfo.builder()
                .method("GET")
                .url("http://localhost:8081/carts/123")
                .headers(sanitizedHeaders)
                .build();
    }

    /**
     * Build response info with truncated body
     */
    private Evidence.ResponseInfo buildResponseInfo() {
        String fullBody = "{\"id\":\"123\",\"items\":[],\"createdAt\":\"2026-01-16T10:00:00Z\"}";
        String truncatedBody = truncateBody(fullBody);

        return Evidence.ResponseInfo.builder()
                .status(200)
                .headers(Map.of("content-type", "application/json"))
                .bodyPreview(truncatedBody)
                .build();
    }

    /**
     * Build trace info
     */
    private Evidence.TraceInfo buildTraceInfo() {
        return Evidence.TraceInfo.builder()
                .traceId("abc123...")
                .spanId("def456...")
                .build();
    }

    /**
     * Redact sensitive values (authorization, tokens, secrets)
     */
    private String redactSecret(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "***redacted***";
    }

    /**
     * Truncate body preview to max length
     */
    private String truncateBody(String body) {
        if (body == null || body.length() <= MAX_BODY_PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_PREVIEW_LENGTH) + "... [truncated]";
    }
}
