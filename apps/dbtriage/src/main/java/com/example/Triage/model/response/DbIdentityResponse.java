package com.example.Triage.model.response;

import java.time.OffsetDateTime;

public record DbIdentityResponse(
        String database,
        String currentUser,
        String sessionUser,
        String serverAddress,
        Integer serverPort,
        String serverVersion,
        OffsetDateTime serverTime,
        String schema) {
}
