package com.example.Triage.model.dto;

import java.time.Instant;

/**
 * Immutable connection context stored in the in-memory registry.
 */
public record DbConnectContext(
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
