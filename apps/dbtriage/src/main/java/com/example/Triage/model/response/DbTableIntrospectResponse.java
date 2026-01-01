package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.dto.DbConstraint;
import com.example.Triage.model.dto.DbIndex;

import lombok.Builder;

@Builder
public record DbTableIntrospectResponse(
                String schema,
                String table,
                List<DbIndex> indexes,
                List<DbConstraint> constraints) {
}
