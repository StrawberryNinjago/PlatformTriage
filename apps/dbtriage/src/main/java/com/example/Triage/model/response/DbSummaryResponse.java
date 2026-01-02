package com.example.Triage.model.response;

import com.example.Triage.model.dto.DbIdentityDto;
import com.example.Triage.model.dto.FlywaySummaryDto;
import com.example.Triage.model.dto.SchemaSummaryDto;

public record DbSummaryResponse(
                DbIdentityDto identity,
                FlywaySummaryDto flyway,
                SchemaSummaryDto publicSchema) {
}