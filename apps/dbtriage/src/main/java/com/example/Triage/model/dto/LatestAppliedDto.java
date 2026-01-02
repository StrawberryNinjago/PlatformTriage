package com.example.Triage.model.dto;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record LatestAppliedDto(
                Integer installedRank,
                String version,
                String description,
                String script,
                OffsetDateTime installedOn,
                String installedBy) {
}
