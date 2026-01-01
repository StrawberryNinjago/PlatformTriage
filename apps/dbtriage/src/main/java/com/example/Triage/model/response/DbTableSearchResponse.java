package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.dto.TableInfo;

public record DbTableSearchResponse(
                String schema,
                String queryString,
                List<TableInfo> tables) {
}
