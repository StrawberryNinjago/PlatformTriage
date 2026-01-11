package com.example.platformtriage.detection;

/**
 * Normalized view of a pod (minimal fields needed by detectors).
 * 
 * This is NOT a K8s client type - it's a simple record that can be:
 * - Built from V1Pod
 * - Created directly in tests
 * - Easily mocked/stubbed
 */
public record PodView(
    String name,
    String phase,              // Running, Pending, Failed, Succeeded, Unknown
    String reason,             // CrashLoopBackOff, ImagePullBackOff, etc.
    boolean ready,
    int restartCount
) {
    public boolean isRunning() {
        return "Running".equalsIgnoreCase(phase);
    }
    
    public boolean isPending() {
        return "Pending".equalsIgnoreCase(phase);
    }
    
    public boolean hasCrashLoopBackOff() {
        return "CrashLoopBackOff".equalsIgnoreCase(reason);
    }
    
    public boolean hasImagePullBackOff() {
        return "ImagePullBackOff".equalsIgnoreCase(reason) 
            || "ErrImagePull".equalsIgnoreCase(reason);
    }
}
