package com.example.Triage.model.dto;

import java.time.Instant;

public record DbConnectContextDto(
                String id,
                String host,
                int port,
                String database,
                String username,
                String password,
                String sslMode,
                String schema,
                Instant createdAt) {
}
