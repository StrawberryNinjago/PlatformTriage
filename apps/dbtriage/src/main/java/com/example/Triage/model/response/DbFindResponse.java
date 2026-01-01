package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.dto.DbConstraint;
import com.example.Triage.model.dto.DbIndex;

public record DbFindResponse(
                String schema,
                String nameContains,
                List<DbIndex> indexes,
                List<DbConstraint> constraints) {

}
