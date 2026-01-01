package com.example.Triage.model.response;

import com.example.Triage.model.dto.DbConstraint;

public record DbConstraintsResponse(
                String schema,
                String table,
                java.util.List<DbConstraint> constraints) {
}
