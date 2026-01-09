package com.example.platformtriage.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evidence object pointing to specific Kubernetes resources related to a finding.
 * Every finding must include evidence (pods, events, deployments, etc.)
 */
public record Evidence(
    @JsonProperty("kind") String kind,      // e.g., "Pod", "Event", "Deployment", "Service"
    @JsonProperty("name") String name,      // e.g., "kv-misconfig-app-c5f9cc746-r2x8g"
    @JsonProperty("message") String message // Optional: event message or additional context
) {
    public Evidence(String kind, String name) {
        this(kind, name, null);
    }
}

