package com.example.platformtriage.model.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.dto.Health;
import com.example.platformtriage.model.dto.Objects;
import com.example.platformtriage.model.dto.Target;

/**
 * Response contract for deployment triage summary.
 * 
 * PRIMARY FAILURE CONTRACT:
 * - primaryFailure is set ONLY when health.overall == FAIL or health.overall == UNKNOWN
 * - primaryFailure = null when health.overall == WARN or health.overall == PASS
 * - This prevents warnings from being treated as critical failures in the UI
 * 
 * TOP WARNING CONTRACT:
 * - topWarning is set when there are WARN-severity findings (typically when overall == WARN)
 * - topWarning = highest priority finding with severity == WARN
 * - topWarning can be set even when overall == PASS (warnings present but no errors)
 * - This gives UI a deterministic "top warning" without re-implementing prioritization
 * 
 * DEBUG METADATA:
 * - primaryFailureDebug explains WHY the primary failure was chosen
 * - Includes score breakdown and competing findings
 * - Helps users/developers understand the ranking algorithm
 */
public record DeploymentSummaryResponse(
    OffsetDateTime timestamp,
    Target target,
    Health health,
    List<Finding> findings,
    Finding primaryFailure,  // Set ONLY when overall == FAIL or UNKNOWN; null otherwise
    Finding topWarning,      // Highest priority WARN-severity finding; null if no warnings
    PrimaryFailureDebug primaryFailureDebug,  // Debug metadata (why this was chosen)
    Objects objects
) {}
