package com.example.Triage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.Triage.handler.DbTablesHandler;
import com.example.Triage.model.response.DbTableIntrospectResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TableDetailsSkill implements DbTriageSkill {

    private final DbTablesHandler tablesHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.TABLE_DETAILS,
                "Inspect table metadata including indexes and columns.",
                true,
                List.of("tableName")
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        String tableName = context.parameter("tableName");
        if (!StringUtils.hasText(tableName)) {
            return new DbTriageSkillResult(
                    "clarify",
                    "I can inspect a table, but I need a table name.",
                    List.of("Missing table name."),
                    List.of("Try: show table details for cart_item"),
                    List.of("Which table should I inspect?"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        DbTableIntrospectResponse response = tablesHandler.introspectTable(
                context.connectionId(),
                context.parameter("schema"),
                tableName
        );

        String schema = context.parameter("schema");
        List<String> findings = new ArrayList<>();
        findings.add("Table: " + schema + "." + tableName);
        findings.add("Owner: " + safe(response.owner()));
        if (response.columns() != null) {
            findings.add("Columns: " + response.columns().size());
        }
        if (response.indexes() != null) {
            findings.add("Indexes: " + response.indexes().size());
        }

        return new DbTriageSkillResult(
                "tool",
                "Table diagnostics loaded.",
                findings,
                List.of(
                        "Check privileges if access is restricted.",
                        "Need ownership/privileges next: ask \"Do I have permission for " + tableName + "?\""),
                List.of(
                        "Need another table’s details?",
                        "Want to verify table privileges next?"),
                metadata().tool(),
                true,
                response
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
