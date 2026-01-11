package com.example.platformtriage.detection.detectors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.platformtriage.detection.ClusterSnapshot;
import com.example.platformtriage.detection.DetectionContext;
import com.example.platformtriage.detection.Detector;
import com.example.platformtriage.detection.EventFindingMapper;
import com.example.platformtriage.detection.EventView;
import com.example.platformtriage.model.dto.Evidence;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.FailureCode;

/**
 * Event-driven detector: uses EventFindingMapper to map warning events to failure codes.
 * 
 * This detector centralizes event → finding mapping:
 * - External secret resolution failures (CSI / Key Vault)
 * - Bad config (non-CSI secret/configmap missing)
 * - RBAC denied
 * - Pod sandbox recycle
 * - Any other event-based failures
 * 
 * The mapping rules are defined in EventFindingMapper, keeping this detector clean.
 */
public class EventDrivenDetector implements Detector {
    
    private final EventFindingMapper mapper;
    
    public EventDrivenDetector(EventFindingMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public String id() {
        return "event-driven";
    }
    
    @Override
    public List<Finding> detect(ClusterSnapshot snapshot, DetectionContext ctx) {
        // Map events to failure codes
        Map<FailureCode, EventGroup> eventGroups = new LinkedHashMap<>();
        
        for (EventView event : snapshot.warningEvents()) {
            mapper.map(event).ifPresent(mapped -> {
                eventGroups.computeIfAbsent(
                    mapped.code(),
                    code -> new EventGroup(mapped, new ArrayList<>())
                ).events.add(event);
            });
        }
        
        // Build findings from grouped events
        List<Finding> findings = new ArrayList<>();
        
        for (EventGroup group : eventGroups.values()) {
            findings.add(buildFinding(group, ctx));
        }
        
        return findings;
    }
    
    private Finding buildFinding(EventGroup group, DetectionContext ctx) {
        EventFindingMapper.MappedFailure mapped = group.mappedFailure;
        List<EventView> events = group.events;
        
        // Build evidence from events
        List<Evidence> evidence = new ArrayList<>();
        for (EventView event : events) {
            if (event.involvedObject() != null) {
                evidence.add(new Evidence(
                    "Event",
                    event.involvedObject().name(),
                    event.message()
                ));
            }
        }
        
        // Build explanation
        String explanation = buildExplanation(mapped.code(), events.size());
        
        // Build next steps
        List<String> nextSteps = buildNextSteps(mapped.code(), ctx);
        
        return new Finding(
            mapped.code(),
            mapped.severity(),
            mapped.owner(),
            mapped.titleTemplate(),
            explanation,
            evidence,
            nextSteps
        );
    }
    
    private String buildExplanation(FailureCode code, int eventCount) {
        return switch (code) {
            case EXTERNAL_SECRET_RESOLUTION_FAILED ->
                "Pod cannot mount external secrets via SecretProviderClass; container will not start.";
            case BAD_CONFIG ->
                "Pod cannot start due to missing or invalid Kubernetes configuration (Secret/ConfigMap/volume references).";
            case RBAC_DENIED ->
                "Tool or workload is denied by Kubernetes RBAC for required operations.";
            case POD_SANDBOX_RECYCLE ->
                "Pod sandbox changed and pod will be killed and re-created. This may indicate node-level issues, "
                + "runtime problems, or network policy changes.";
            default ->
                eventCount + " warning event" + (eventCount > 1 ? "s" : "") + " detected.";
        };
    }
    
    private List<String> buildNextSteps(FailureCode code, DetectionContext ctx) {
        return switch (code) {
            case EXTERNAL_SECRET_RESOLUTION_FAILED -> List.of(
                "Confirm SecretProviderClass exists in the same namespace: kubectl get secretproviderclass -n " + ctx.namespace(),
                "Verify Key Vault name/URI and object names match exactly (case-sensitive).",
                "Verify workload identity/managed identity has 'Get' permission on secrets in Key Vault.",
                "Check tenant ID and client ID match in federated identity binding.",
                "Confirm CSI driver is installed: kubectl get pods -n kube-system | grep csi-secrets-store",
                "Check pod service account is correctly annotated for workload identity."
            );
            case BAD_CONFIG -> List.of(
                "Verify referenced Secret/ConfigMap exists in the namespace: kubectl get secrets,configmaps -n " + ctx.namespace(),
                "Verify key names in Secret/ConfigMap match the keys referenced in pod spec.",
                "Check volumeMount names match volume definitions.",
                "If using Helm: verify values rendered to expected resource names.",
                "Review pod spec for typos in secret/configmap references."
            );
            case RBAC_DENIED -> List.of(
                "Confirm triage service account has list/get/watch permissions for required resources.",
                "Check workload service account permissions: kubectl describe serviceaccount <sa> -n " + ctx.namespace(),
                "Review ClusterRole/Role bindings: kubectl get clusterrolebindings,rolebindings -n " + ctx.namespace(),
                "Test access: kubectl auth can-i <verb> <resource> --as=system:serviceaccount:" + ctx.namespace() + ":<sa>",
                "Check pod security policies or admission controllers that may block operations."
            );
            case POD_SANDBOX_RECYCLE -> List.of(
                "Check node health: kubectl describe node <node>",
                "Review container runtime logs on the node.",
                "Check for network policy or CNI changes.",
                "Look for node resource pressure or eviction events.",
                "Verify pod security policies or admission webhooks."
            );
            default -> List.of(
                "Review event details in the evidence section.",
                "Check pod status: kubectl get pods -n " + ctx.namespace(),
                "Describe affected resources: kubectl describe <resource> -n " + ctx.namespace()
            );
        };
    }
    
    private record EventGroup(
        EventFindingMapper.MappedFailure mappedFailure,
        List<EventView> events
    ) {}
}
