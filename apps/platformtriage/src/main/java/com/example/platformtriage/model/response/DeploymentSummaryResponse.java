package com.example.platformtriage.model.response;

import com.example.platformtriage.model.dto.Target;
import com.example.platformtriage.model.dto.Health;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.dto.Objects;
import com.example.platformtriage.model.enums.OverallStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DeploymentSummaryResponse(
    OffsetDateTime timestamp,
    Target target,
    Health health,
    List<Finding> findings,
    Objects objects
) {}
