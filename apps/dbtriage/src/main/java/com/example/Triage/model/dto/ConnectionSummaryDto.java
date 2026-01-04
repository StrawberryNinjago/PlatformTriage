package com.example.Triage.model.dto;

/**
 * Summary of a connection (without sensitive data like password)
 */
public record ConnectionSummaryDto(
        String connectionId,
        String host,
        int port,
        String database,
        String username,
        String schema,
        String createdAt) {
}

