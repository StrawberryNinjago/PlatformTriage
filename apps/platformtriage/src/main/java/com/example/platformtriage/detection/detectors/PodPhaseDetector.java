package com.example.platformtriage.detection.detectors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.platformtriage.detection.ClusterSnapshot;
import com.example.platformtriage.detection.DetectionContext;
import com.example.platformtriage.detection.Detector;
import com.example.platformtriage.detection.EventView;
import com.example.platformtriage.detection.PodView;
import com.example.platformtriage.model.dto.Evidence;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.FailureCode;

/**
 * Detects failures based on pod phase and reason.
 * 
 * Handles:
 * - IMAGE_PULL_FAILED (ImagePullBackOff, ErrImagePull)
 * - CRASH_LOOP (CrashLoopBackOff, BackOff events)
 * - READINESS_CHECK_FAILED (Running but not ready)
 * - INSUFFICIENT_RESOURCES (Pending pods)
 */
public class PodPhaseDetector implements Detector {
    
    @Override
    public String id() {
        return "pod-phase";
    }
    
    @Override
    public List<Finding> detect(ClusterSnapshot snapshot, DetectionContext ctx) {
        List<Finding> findings = new ArrayList<>();
        
        // Detect BackOff pods from events
        Set<String> backoffPods = detectBackoffPods(snapshot);
        
        findings.addAll(detectImagePullFailed(snapshot, ctx));
        findings.addAll(detectCrashLoop(snapshot, backoffPods, ctx));
        findings.addAll(detectReadinessCheckFailed(snapshot, ctx));
        findings.addAll(detectInsufficientResources(snapshot, ctx));
        
        return findings;
    }
    
    private Set<String> detectBackoffPods(ClusterSnapshot snapshot) {
        Set<String> backoffPods = new HashSet<>();
        
        for (EventView event : snapshot.warningEvents()) {
            if ("BackOff".equalsIgnoreCase(event.reason()) 
                && event.involvedObject() != null
                && "Pod".equals(event.involvedObject().kind())) {
                backoffPods.add(event.involvedObject().name());
            }
        }
        
        return backoffPods;
    }
    
    private List<Finding> detectImagePullFailed(ClusterSnapshot snapshot, DetectionContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        
        for (PodView pod : snapshot.pods()) {
            if (pod.hasImagePullBackOff()) {
                evidence.add(new Evidence("Pod", pod.name()));
            }
        }
        
        if (evidence.isEmpty()) {
            return List.of();
        }
        
        return List.of(new Finding(
            FailureCode.IMAGE_PULL_FAILED,
            "Image pull failed",
            "Container image cannot be pulled (authentication, missing tag, or registry access issue).",
            evidence,
            List.of(
                "Verify image tag exists in the registry.",
                "Verify imagePullSecrets are configured if using a private registry.",
                "Check network/egress policy allows access to the registry.",
                "Confirm registry URL is correct (typos in image name/tag).",
                "If using ACR: verify AKS has pull permissions via managed identity or service principal.",
                "Test image pull manually: docker pull <image:tag>"
            )
        ));
    }
    
    private List<Finding> detectCrashLoop(ClusterSnapshot snapshot, Set<String> backoffPods, DetectionContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        
        for (PodView pod : snapshot.pods()) {
            if (pod.hasCrashLoopBackOff() || backoffPods.contains(pod.name())) {
                String msg = "Restarts: " + pod.restartCount();
                if (pod.reason() != null) {
                    msg += ", Reason: " + pod.reason();
                }
                evidence.add(new Evidence("Pod", pod.name(), msg));
            }
        }
        
        if (evidence.isEmpty()) {
            return List.of();
        }
        
        return List.of(new Finding(
            FailureCode.CRASH_LOOP,
            "Crash loop detected",
            "Containers are repeatedly crashing (CrashLoopBackOff, OOM, or non-zero exit codes).",
            evidence,
            List.of(
                "Inspect last termination reason: kubectl describe pod <pod> -n " + ctx.namespace(),
                "Check logs from previous container instance: kubectl logs <pod> -n " + ctx.namespace() + " --previous",
                "Look for OOMKilled (out of memory): increase memory limits if needed.",
                "Validate required environment variables are set correctly.",
                "Check for application startup errors or missing dependencies.",
                "Review exit codes: 137 = OOMKilled, 143 = SIGTERM, others = app error"
            )
        ));
    }
    
    private List<Finding> detectReadinessCheckFailed(ClusterSnapshot snapshot, DetectionContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        
        for (PodView pod : snapshot.pods()) {
            if (pod.isRunning() && !pod.ready()) {
                evidence.add(new Evidence("Pod", pod.name()));
            }
        }
        
        if (evidence.isEmpty()) {
            return List.of();
        }
        
        return List.of(new Finding(
            FailureCode.READINESS_CHECK_FAILED,
            "Readiness check failed",
            "Pods are running but never become Ready (readiness probe or application health check failing).",
            evidence,
            List.of(
                "Verify readiness probe path/port matches the application endpoint.",
                "Check application logs for startup errors: kubectl logs <pod> -n " + ctx.namespace(),
                "Verify dependencies are reachable (database, cache, external APIs).",
                "Confirm service port mapping matches container port.",
                "Test readiness endpoint manually: kubectl exec <pod> -- wget -O- localhost:<port>/<path>",
                "Check if initialDelaySeconds is too short for app startup time."
            )
        ));
    }
    
    private List<Finding> detectInsufficientResources(ClusterSnapshot snapshot, DetectionContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        
        for (PodView pod : snapshot.pods()) {
            if (pod.isPending()) {
                evidence.add(new Evidence("Pod", pod.name(), "Phase: Pending"));
            }
        }
        
        if (evidence.isEmpty()) {
            return List.of();
        }
        
        return List.of(new Finding(
            FailureCode.INSUFFICIENT_RESOURCES,
            "Insufficient resources",
            "Pod scheduling is blocked or evicted due to insufficient CPU/memory, node capacity, or quotas.",
            evidence,
            List.of(
                "Check resource requests/limits vs node capacity: kubectl describe nodes",
                "Check namespace resource quotas: kubectl get resourcequotas -n " + ctx.namespace(),
                "Check node taints/tolerations: kubectl describe nodes | grep Taint",
                "View pending pod scheduling issues: kubectl describe pod <pod> -n " + ctx.namespace(),
                "Consider reducing resource requests or scaling cluster nodes.",
                "Check for pod disruption budgets that may block evictions."
            )
        ));
    }
}
