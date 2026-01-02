package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record RolePostureDto(
        String currentUser,
        boolean isSuperuser,
        boolean canCreateRole,
        boolean canCreateDb,
        boolean canReplicate,
        boolean canBypassRls) {
}