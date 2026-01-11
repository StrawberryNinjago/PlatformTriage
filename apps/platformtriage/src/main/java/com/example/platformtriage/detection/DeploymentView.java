package com.example.platformtriage.detection;

import java.util.List;

/**
 * Normalized view of a deployment.
 */
public record DeploymentView(
    String name,
    int desiredReplicas,
    int readyReplicas,
    List<DeploymentCondition> conditions
) {
    public record DeploymentCondition(
        String type,          // Available, Progressing, ReplicaFailure
        String status,        // True, False, Unknown
        String reason         // NewReplicaSetAvailable, ProgressDeadlineExceeded, etc.
    ) {}
    
    public boolean isRolloutStuck() {
        return conditions.stream()
            .anyMatch(c -> "Progressing".equals(c.type())
                && "False".equalsIgnoreCase(c.status())
                && "ProgressDeadlineExceeded".equalsIgnoreCase(c.reason()));
    }
    
    public boolean hasNoReadyPods() {
        return desiredReplicas > 0 && readyReplicas == 0;
    }
}
