package com.example.platformtriage.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeploymentTraceSearchResponse(
    @JsonProperty("namespace") String namespace,
    @JsonProperty("traceId") String traceId,
    @JsonProperty("matches") List<DeploymentTraceMatch> matches,
    @JsonProperty("searchedPods") int searchedPods,
    @JsonProperty("totalMatches") int totalMatches
) {}

