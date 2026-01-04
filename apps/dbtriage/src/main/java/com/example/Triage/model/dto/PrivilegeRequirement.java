package com.example.Triage.model.dto;

import java.util.List;

/**
 * Detailed privilege requirement information
 */
public record PrivilegeRequirement(
        String capability,
        String missingPrivilege,
        String reason,
        List<String> requiredGrants) {
}

