package com.example.platformtriage.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Tracks pod restart counts per scope (namespace + selector/release) to detect
 * restarts *since last LOAD*, not cumulative restarts since pod creation.
 * 
 * Why this matters:
 * - Kubernetes restart count is cumulative (never resets until pod is deleted)
 * - Without baseline tracking, a pod that restarted once will WARN forever
 * - With baseline tracking, we only warn on NEW restarts
 * 
 * Design:
 * - First time we see a pod: baseline = current (delta = 0, no false positive)
 * - Subsequent times: delta = current - baseline
 * - TTL eviction prevents memory bloat when pods are replaced
 */
@Component
public class RestartBaselineStore {
    
    /**
     * Scope identifies a query context (namespace + selector/release).
     * Baselines don't cross-contaminate between different filters.
     */
    public record ScopeKey(String namespace, String selector, String release) {}
    
    /**
     * Unique key for a pod within a scope.
     */
    public record PodKey(ScopeKey scope, String podName) {}
    
    /**
     * Baseline data: last observed restart count + timestamp.
     */
    public record Baseline(int restarts, Instant updatedAt) {}
    
    private final Map<PodKey, Baseline> baselines = new ConcurrentHashMap<>();
    
    /**
     * TTL for baseline entries.
     * Long enough for interactive debugging (2 hours), short enough to auto-clean.
     * Pod names change on rollout, so eviction is critical to avoid memory bloat.
     */
    private final Duration ttl = Duration.ofHours(2);
    
    /**
     * Returns delta since last time we saw this pod in this scope,
     * and updates baseline to currentRestarts.
     * 
     * First time: baseline is set to current => delta = 0 (prevents false warnings on first load).
     * Subsequent times: delta = current - baseline.
     * 
     * @param scope The query scope (namespace + selector/release)
     * @param podName The pod name
     * @param currentRestarts Current restart count from Kubernetes
     * @param now Current timestamp
     * @return Delta restarts since last load (0 or positive)
     */
    public int deltaAndUpdate(ScopeKey scope, String podName, int currentRestarts, Instant now) {
        PodKey key = new PodKey(scope, podName);
        
        Baseline prev = baselines.get(key);
        
        // First time seeing this pod: baseline = current (delta = 0)
        // This prevents false positives on first load
        int prevRestarts = (prev == null) ? currentRestarts : prev.restarts();
        
        int delta = Math.max(0, currentRestarts - prevRestarts);
        
        // Update baseline
        baselines.put(key, new Baseline(currentRestarts, now));
        
        return delta;
    }

    /**
     * Evict expired baselines (older than TTL).
     * Call this periodically (e.g., at start of each query) to prevent memory bloat.
     * 
     * @param now Current timestamp
     */
    public void evictExpired(Instant now) {
        baselines.entrySet().removeIf(e -> 
            e.getValue().updatedAt().isBefore(now.minus(ttl))
        );
    }
    
    /**
     * Get current baseline count (for testing/debugging).
     */
    public int size() {
        return baselines.size();
    }
    
    /**
     * Clear all baselines (for testing).
     */
    public void clear() {
        baselines.clear();
    }
}
