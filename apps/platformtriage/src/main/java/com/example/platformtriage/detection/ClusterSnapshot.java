package com.example.platformtriage.detection;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalized, immutable snapshot of cluster state.
 * 
 * This is a pure data structure (no K8s client dependencies) that can be:
 * - Built from real K8s API responses
 * - Hand-crafted in unit tests (20 lines)
 * - Serialized/deserialized for debugging
 * 
 * View types (PodView, EventView, etc.) are simple records with only the fields
 * detectors need. This keeps tests clean and avoids coupling to K8s client types.
 */
public record ClusterSnapshot(
    List<PodView> pods,
    List<DeploymentView> deployments,
    List<EventView> events,
    List<ServiceView> services,
    List<EndpointsView> endpoints
) {
    // Convenience indexes for detectors (lazily computed)
    private static final String EVENT_KEY_SEPARATOR = "/";
    
    /**
     * Index events by involved object (kind/name).
     * Key format: "Pod/my-pod-abc123" or "Deployment/my-deployment"
     */
    public Map<String, List<EventView>> eventsByInvolvedObjectKey() {
        return events.stream()
            .filter(e -> e.involvedObject() != null)
            .collect(Collectors.groupingBy(e -> 
                e.involvedObject().kind() + EVENT_KEY_SEPARATOR + e.involvedObject().name()
            ));
    }
    
    /**
     * Get events for a specific pod by name.
     */
    public List<EventView> eventsForPod(String podName) {
        return events.stream()
            .filter(e -> e.involvedObject() != null)
            .filter(e -> "Pod".equals(e.involvedObject().kind()))
            .filter(e -> podName.equals(e.involvedObject().name()))
            .toList();
    }
    
    /**
     * Get events for a specific deployment by name.
     */
    public List<EventView> eventsForDeployment(String deploymentName) {
        return events.stream()
            .filter(e -> e.involvedObject() != null)
            .filter(e -> "Deployment".equals(e.involvedObject().kind()))
            .filter(e -> deploymentName.equals(e.involvedObject().name()))
            .toList();
    }
    
    /**
     * Get warning events only.
     */
    public List<EventView> warningEvents() {
        return events.stream()
            .filter(e -> "Warning".equalsIgnoreCase(e.type()))
            .toList();
    }
}
