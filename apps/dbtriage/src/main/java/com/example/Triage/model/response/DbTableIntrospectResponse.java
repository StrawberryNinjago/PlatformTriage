package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.dto.DbConstraint;
import com.example.Triage.model.dto.DbIndex;
import com.example.Triage.model.dto.DbTableColumn;

import lombok.Builder;

@Builder
public record DbTableIntrospectResponse(
                String schema,
                String table,
                String owner,
                String currentUser,
                List<DbTableColumn> columns,
                List<DbIndex> indexes,
                List<DbConstraint> constraints,
                FlywayMigrationInfo flywayInfo) {
        
        @Builder
        public record FlywayMigrationInfo(
                String installedBy,
                String installedOn,
                String version,
                String description) {
        }
}
