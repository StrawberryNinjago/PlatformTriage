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
 * TOP WARNING (UI DERIVATION):
 * - When health.overall == WARN, UI should derive "top warning" as:
 *   the first finding with severity == WARN in the findings list
 * - This keeps backend logic simple while giving UI flexibility
 */
public record DeploymentSummaryResponse(
    OffsetDateTime timestamp,
    Target target,
    Health health,
    List<Finding> findings,
    Finding primaryFailure,  // Set ONLY when overall == FAIL or UNKNOWN; null otherwise
    Objects objects
) {}
