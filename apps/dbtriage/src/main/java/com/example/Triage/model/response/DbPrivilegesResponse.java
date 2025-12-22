package com.example.Triage.model.response;

import java.util.List;

public record DbPrivilegesResponse(
        String schema,
        String table,
        ValidationStatus status,
        String owner,
        String currentUser,
        List<String> grantedPrivileges,
        List<String> missingPrivileges,
        String message) {

    public enum ValidationStatus {
        PASS,
        FAIL,
        WARNING
    }
}

