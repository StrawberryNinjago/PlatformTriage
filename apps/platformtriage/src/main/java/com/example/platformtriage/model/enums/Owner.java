package com.example.platformtriage.model.enums;

/**
 * Ownership enum for routing findings to the appropriate team.
 * Used for escalation and responsibility assignment.
 */
public enum Owner {
    /**
     * Application team owns the issue (app code, config, readiness probes)
     */
    APP,
    
    /**
     * Platform/DevOps team owns the issue (infrastructure, K8s, networking, resources)
     */
    PLATFORM,
    
    /**
     * Security team owns the issue (RBAC, policies, permissions)
     */
    SECURITY,
    
    /**
     * Ownership unclear or not yet determined
     */
    UNKNOWN
}

