package com.example.platformtriage.model.enums;

/**
 * Platform Failure Taxonomy - MVP 8 codes + Tooling/Query failures
 * Mutually exclusive primary failure codes that are:
 * - Owner-routable (clear escalation path)
 * - Evidence-driven (every finding must point to pods/events/deployments)
 * - Composable (multiple findings per run)
 */
public enum FailureCode {
    /**
     * Query failed: Invalid input, bad selector syntax, namespace issues, or API 400/422 errors.
     * Owner: Platform (tooling) | Default severity: ERROR
     * Priority: 0 (highest - cannot assess system if query fails)
     */
    QUERY_INVALID(Owner.PLATFORM, Severity.ERROR),
    
    /**
     * Pod cannot start due to missing/invalid K8s config (Secret/ConfigMap/env/volume refs).
     * Owner: App team | Default severity: ERROR
     */
    BAD_CONFIG(Owner.APP, Severity.ERROR),
    
    /**
     * Workload cannot mount/load external secrets (AKV + CSI + SecretProviderClass + identity).
     * Owner: Platform/DevOps | Default severity: ERROR
     */
    EXTERNAL_SECRET_RESOLUTION_FAILED(Owner.PLATFORM, Severity.ERROR),
    
    /**
     * Image cannot be pulled (auth, tag missing, registry access).
     * Owner: Platform/DevOps | Default severity: ERROR
     */
    IMAGE_PULL_FAILED(Owner.PLATFORM, Severity.ERROR),
    
    /**
     * Pods run but never become Ready (readiness probe / app health).
     * Owner: App team | Default severity: ERROR
     */
    READINESS_CHECK_FAILED(Owner.APP, Severity.ERROR),
    
    /**
     * Containers repeatedly crash (CrashLoopBackOff / OOM / exit codes).
     * Owner: App team (sometimes Platform for OOM) | Default severity: ERROR
     */
    CRASH_LOOP(Owner.APP, Severity.ERROR),
    
    /**
     * Service has zero endpoints due to label/selector mismatch or readiness gating.
     * Owner: App team | Default severity: WARN
     */
    SERVICE_SELECTOR_MISMATCH(Owner.APP, Severity.WARN),
    
    /**
     * Scheduling blocked or evictions due to CPU/memory/node capacity/quotas.
     * Owner: Platform/DevOps | Default severity: ERROR
     */
    INSUFFICIENT_RESOURCES(Owner.PLATFORM, Severity.ERROR),
    
    /**
     * Tool or workload is denied by Kubernetes RBAC (for required reads/operations).
     * Owner: Platform/Security | Default severity: ERROR
     */
    RBAC_DENIED(Owner.SECURITY, Severity.ERROR),
    
    /**
     * Risk signals / Advisory findings (WARN severity - do not fail overall)
     */
    POD_RESTARTS_DETECTED(Owner.APP, Severity.WARN),
    POD_SANDBOX_RECYCLE(Owner.PLATFORM, Severity.WARN),
    
    /**
     * Legacy/catch-all codes for backward compatibility
     */
    NO_MATCHING_OBJECTS(Owner.UNKNOWN, Severity.WARN),  // WARN: Cannot assess without objects
    ROLLOUT_STUCK(Owner.APP, Severity.ERROR),
    NO_READY_PODS(Owner.APP, Severity.ERROR);
    
    private final Owner defaultOwner;
    private final Severity defaultSeverity;
    
    FailureCode(Owner owner, Severity severity) {
        this.defaultOwner = owner;
        this.defaultSeverity = severity;
    }
    
    public Owner getDefaultOwner() {
        return defaultOwner;
    }
    
    public Severity getDefaultSeverity() {
        return defaultSeverity;
    }
    
    /**
     * Priority order for primary failure selection.
     * Lower values = higher priority (hard blockers that prevent container start)
     * Risk signals (WARN) have low priority - they don't become primary failures
     */
    public int getPriority() {
        return switch (this) {
            // Query/tooling failures - HIGHEST priority (cannot assess if query fails)
            case QUERY_INVALID -> 0;
            // Application/Platform failures - high priority
            case EXTERNAL_SECRET_RESOLUTION_FAILED -> 1;
            case BAD_CONFIG -> 2;
            case IMAGE_PULL_FAILED -> 3;
            case INSUFFICIENT_RESOURCES -> 4;
            case RBAC_DENIED -> 5;
            case CRASH_LOOP -> 6;
            case READINESS_CHECK_FAILED -> 7;
            case SERVICE_SELECTOR_MISMATCH -> 8;
            case NO_READY_PODS -> 9;
            case ROLLOUT_STUCK -> 10;
            // Risk signals (WARN) - low priority, informational
            case POD_RESTARTS_DETECTED -> 50;
            case POD_SANDBOX_RECYCLE -> 51;
            // Special cases
            case NO_MATCHING_OBJECTS -> 99;
        };
    }
}

