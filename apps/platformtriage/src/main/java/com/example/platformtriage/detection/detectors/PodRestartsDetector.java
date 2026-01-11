package com.example.platformtriage.detection.detectors;

import java.util.ArrayList;
import java.util.List;

import com.example.platformtriage.detection.ClusterSnapshot;
import com.example.platformtriage.detection.DetectionContext;
import com.example.platformtriage.detection.Detector;
import com.example.platformtriage.detection.PodView;
import com.example.platformtriage.model.dto.Evidence;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.FailureCode;

/**
 * Detects pods that have restarted but are currently running and ready.
 * 
 * This is a risk signal (WARN severity):
 * - Indicates transient crashes, OOM, or unstable startup
 * - Not a critical failure (pods are currently working)
 * - Should be investigated to prevent future failures
 */
public class PodRestartsDetector implements Detector {
    
    @Override
    public String id() {
        return "pod-restarts";
    }
    
    @Override
    public List<Finding> detect(ClusterSnapshot snapshot, DetectionContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        int totalRestarts = 0;
        
        // Find pods that are running + ready but have restarts
        for (PodView pod : snapshot.pods()) {
            if (pod.isRunning() && pod.ready() && pod.restartCount() > 0) {
                totalRestarts += pod.restartCount();
                
                String evidenceMsg = pod.restartCount() + " restart" 
                    + (pod.restartCount() > 1 ? "s" : "") 
                    + " (currently Ready)";
                
                if (pod.reason() != null && !pod.reason().isEmpty()) {
                    evidenceMsg += " - Last reason: " + pod.reason();
                }
                
                evidence.add(new Evidence("Pod", pod.name(), evidenceMsg));
            }
        }
        
        if (evidence.isEmpty()) {
            return List.of();
        }
        
        // Build explanation
        String explanation;
        if (evidence.size() == 1) {
            explanation = "Pod has restarted " + totalRestarts + " time" 
                + (totalRestarts > 1 ? "s" : "")
                + " but is currently running. This may indicate transient crashes, "
                + "config reloads, or unstable startup behavior.";
        } else {
            explanation = evidence.size() + " pods have restarted " + totalRestarts 
                + " total times but are currently running. This may indicate transient "
                + "crashes, config reloads, or unstable startup behavior.";
        }
        
        return List.of(new Finding(
            FailureCode.POD_RESTARTS_DETECTED,
            "Pod restarts detected",
            explanation,
            evidence,
            List.of(
                "Review pod logs for crash patterns: kubectl logs <pod> -n " + ctx.namespace() + " --previous",
                "Check if restarts correlate with deployments or config changes.",
                "Verify readiness/liveness probe settings are appropriate for startup time.",
                "Look for OOM events (exit code 137): kubectl describe pod <pod> -n " + ctx.namespace(),
                "Consider if restarts are expected (e.g., app restarts on config reload)."
            )
        ));
    }
}
