package com.example.Triage.model.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request for environment comparison
 */
public record EnvironmentComparisonRequest(
        @NotBlank String sourceConnectionId,
        @NotBlank String targetConnectionId,
        String sourceEnvironmentName,
        String targetEnvironmentName,
        String schema,
        List<String> specificTables) {

    public String schema() {
        return schema != null ? schema : "public";
    }

    public String sourceEnvironmentName() {
        return sourceEnvironmentName != null ? sourceEnvironmentName : "SOURCE";
    }

    public String targetEnvironmentName() {
        return targetEnvironmentName != null ? targetEnvironmentName : "TARGET";
    }
}

