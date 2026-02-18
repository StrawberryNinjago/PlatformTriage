package com.example.platformtriage.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeploymentVersionCheck(
    @JsonProperty("dockerImages") List<String> dockerImages,
    @JsonProperty("databaseVersion") String databaseVersion,
    @JsonProperty("flywayVersion") String flywayVersion,
    @JsonProperty("databaseVersionSource") String databaseVersionSource,
    @JsonProperty("flywayVersionSource") String flywayVersionSource,
    @JsonProperty("notes") String notes
) {}

