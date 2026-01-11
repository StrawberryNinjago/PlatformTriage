package com.example.platformtriage.detection.detectors;

import java.util.List;

import com.example.platformtriage.detection.ClusterSnapshot;
import com.example.platformtriage.detection.DetectionContext;
import com.example.platformtriage.detection.Detector;
import com.example.platformtriage.model.dto.Evidence;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.enums.FailureCode;

/**
 * Detects when no pods or deployments match the query selector.
 * 
 * This is a special case that triggers UNKNOWN status (cannot assess health without objects).
 */
public class NoMatchingObjectsDetector implements Detector {
    
    @Override
    public String id() {
        return "no-matching-objects";
    }
    
    @Override
    public int order() {
        return -1000; // Run first (if no objects, skip other detectors)
    }
    
    @Override
    public List<Finding> detect(ClusterSnapshot snapshot, DetectionContext ctx) {
        // Check if we have any pods or deployments
        if (!snapshot.pods().isEmpty() || !snapshot.deployments().isEmpty()) {
            return List.of(); // Objects found, not a failure
        }
        
        // No objects found - cannot assess health
        return List.of(new Finding(
            FailureCode.NO_MATCHING_OBJECTS,
            "No matching objects",
            "No pods or deployments matched the provided selector/release in this namespace.",
            List.of(new Evidence("Namespace", ctx.namespace())),
            List.of(
                "Verify the selector or release parameter is correct.",
                "Check that resources exist in the namespace: kubectl get pods,deployments -n " + ctx.namespace(),
                "Confirm you're connected to the correct cluster and namespace."
            )
        ));
    }
}
