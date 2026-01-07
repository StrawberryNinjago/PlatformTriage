package com.example.platformtriage.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EventInfo(
        String type,
        String reason,
        String message,
        String involvedObjectKind,
        String involvedObjectName,
        @JsonProperty("timestamp")
        @JsonAlias("lastTimestamp")
        String timestamp
        ) {

}
