package com.example.platformtriage.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeploymentTraceMatch(
    @JsonProperty("podName") String podName,
    @JsonProperty("lines") List<String> lines
) {}

