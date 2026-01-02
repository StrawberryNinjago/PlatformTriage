package com.example.Triage.model.response;

import com.example.Triage.model.dto.DbIdentityDto;
import com.example.Triage.model.dto.WarningMessageDto;
import com.example.Triage.model.dto.FlywaySummaryDto;
import com.example.Triage.model.enums.FlywayStatus;

import java.util.List;
import lombok.Builder;

@Builder
public record DbFlywayHealthResponse(
                FlywayStatus status,
                FlywaySummaryDto flywaySummary,
                DbIdentityDto identity,
                String message,
                String expectedUser,
                List<WarningMessageDto> warnings) {
}
