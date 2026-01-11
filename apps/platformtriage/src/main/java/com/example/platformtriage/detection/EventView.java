package com.example.platformtriage.detection;

import java.time.OffsetDateTime;

/**
 * Normalized view of a Kubernetes event.
 */
public record EventView(
    String type,              // Warning, Normal
    String reason,            // FailedMount, BackOff, SandboxChanged, Failed, etc.
    String message,
    OffsetDateTime lastTimestamp,
    InvolvedObject involvedObject
) {
    public record InvolvedObject(String kind, String name, String namespace) {}
    
    public boolean isWarning() {
        return "Warning".equalsIgnoreCase(type);
    }
    
    public boolean isNormal() {
        return "Normal".equalsIgnoreCase(type);
    }
    
    public String involvedObjectKey() {
        if (involvedObject == null) {
            return null;
        }
        return involvedObject.kind() + "/" + involvedObject.name();
    }
}
