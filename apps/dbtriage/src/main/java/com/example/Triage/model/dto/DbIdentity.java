package com.example.Triage.model.dto;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record DbIdentity(
        String database,
        String user,
        String serverAddr,
        Integer serverPort,
        String serverVersion,
        OffsetDateTime serverTime) {
}
