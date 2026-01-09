package com.example.platformtriage.model.response;

import com.example.platformtriage.model.dto.Target;
import com.example.platformtriage.model.dto.Health;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.dto.Objects;
import java.time.OffsetDateTime;
import java.util.List;

public record DeploymentSummaryResponse(
    OffsetDateTime timestamp,
    Target target,
    Health health,
    List<Finding> findings,
    Finding primaryFailure,  // The highest-priority finding (for quick triage decision)
    Objects objects
) {}
