package com.example.Triage.model.dto;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record DbIdentityDto(
                String database,
                String currentUser,
                String sessionUser,
                String serverAddr,
                Integer serverPort,
                String serverVersion,
                OffsetDateTime serverTime,
                String schema) {
}
