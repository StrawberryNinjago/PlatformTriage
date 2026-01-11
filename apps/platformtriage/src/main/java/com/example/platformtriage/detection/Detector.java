package com.example.platformtriage.detection;

import java.util.List;

import com.example.platformtriage.model.dto.Finding;

/**
 * Core detector interface: rules that emit findings from a cluster snapshot.
 * 
 * Detectors are:
 * - Pure functions (no side effects)
 * - Unit-testable (no K8s client dependencies)
 * - Composable (multiple detectors run independently)
 * - Deterministic (same snapshot → same findings)
 * 
 * Contract:
 * - detect() returns 0-N findings based on the snapshot
 * - Detectors should not depend on execution order
 * - Findings are ranked/prioritized separately by FindingRanker
 */
public interface Detector {
    /**
     * Stable identifier for this detector (used in debug metadata and tests).
     * Should be unique across all detectors.
     */
    String id();
    
    /**
     * Optional execution order (lower = earlier).
     * Used as tie-breaker when multiple detectors want to run.
     * Most detectors can return 0 (order doesn't matter).
     */
    default int order() {
        return 0;
    }
    
    /**
     * Detect failures/issues in the cluster snapshot.
     * 
     * @param snapshot Normalized view of cluster state (pods, events, services, etc.)
     * @param ctx Detection context (namespace, selector, release, clock)
     * @return List of findings (0-N), empty if no issues detected
     */
    List<Finding> detect(ClusterSnapshot snapshot, DetectionContext ctx);
}
