package com.example.platformtriage.model.dto;

import com.example.platformtriage.model.enums.FailureCode;
import com.example.platformtriage.model.enums.Owner;
import com.example.platformtriage.model.enums.Severity;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Finding object representing a detected failure or issue in the platform.
 * 
 * Contract:
 * - code: One of the 8 taxonomy failure codes (or legacy codes)
 * - severity: ERROR, WARN, or INFO
 * - owner: APP, PLATFORM, SECURITY, or UNKNOWN
 * - title: Short, actionable summary (1 line)
 * - explanation: Detailed description of what's wrong
 * - evidence: List of Kubernetes objects involved (pods, events, etc.)
 * - nextSteps: Actionable troubleshooting steps (2-5 bullets)
 */
public record Finding(
    @JsonProperty("code") FailureCode code,
    @JsonProperty("severity") Severity severity,
    @JsonProperty("owner") Owner owner,
    @JsonProperty("title") String title,
    @JsonProperty("explanation") String explanation,
    @JsonProperty("evidence") List<Evidence> evidence,
    @JsonProperty("nextSteps") List<String> nextSteps
) {
    /**
     * Constructor using default owner and severity from FailureCode
     */
    public Finding(FailureCode code, String title, String explanation, 
                   List<Evidence> evidence, List<String> nextSteps) {
        this(code, code.getDefaultSeverity(), code.getDefaultOwner(), 
             title, explanation, evidence, nextSteps);
    }
    
    /**
     * Get priority for primary failure selection (lower = higher priority)
     */
    public int getPriority() {
        return code.getPriority();
    }
}
