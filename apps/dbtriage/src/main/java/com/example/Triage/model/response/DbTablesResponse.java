package com.example.Triage.model.response;

import java.util.List;

import com.example.Triage.model.dto.TableInfo;

public record DbTablesResponse(
                String schema,
                List<TableInfo> tables) {
}
