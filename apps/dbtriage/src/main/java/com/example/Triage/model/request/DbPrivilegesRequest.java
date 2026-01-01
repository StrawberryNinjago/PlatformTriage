package com.example.Triage.model.request;

import com.example.Triage.model.enums.DbSchema;

import lombok.Builder;

@Builder
public record DbPrivilegesRequest(
        String connectionId,
        DbSchema schema,
        String schemaName, // only used when schema == CUSTOM
        String tableName) {
}
