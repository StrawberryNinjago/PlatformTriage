package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.enums.ValidationStatus;

public record DbPrivilegesResponse(
        String schema,
        String table,
        ValidationStatus status,
        String owner,
        String currentUser,
        List<String> grantedPrivileges,
        List<String> missingPrivileges,
        String message) {
}
