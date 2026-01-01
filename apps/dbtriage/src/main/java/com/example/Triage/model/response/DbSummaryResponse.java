package com.example.Triage.model.response;

import com.example.Triage.model.dto.DbIdentity;
import com.example.Triage.model.dto.FlywaySummary;
import com.example.Triage.model.dto.SchemaSummary;

public record DbSummaryResponse(
        DbIdentity identity,
        FlywaySummary flyway,
        SchemaSummary publicSchema) {
}