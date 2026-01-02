package com.example.Triage.model.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record DbPrivilegeSummaryDto(
                RolePostureDto rolePosture,
                SchemaPrivilegesDto schemaPrivileges,
                List<ObjectPrivilegeCheckDto> objectChecks,
                List<String> memberOfRoles,
                List<WarningMessageDto> warnings) {
}
