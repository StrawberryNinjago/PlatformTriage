package com.example.Triage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.Triage.handler.DbTablesHandler;
import com.example.Triage.model.response.DbTablesResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ListTablesSkill implements DbTriageSkill {

    private final DbTablesHandler tablesHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.LIST_TABLES,
                "List tables in a schema and return an overview.",
                true
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        DbTablesResponse response = tablesHandler.listTables(
                context.connectionId(),
                context.parameter("schema")
        );
        int count = response.tables() == null ? 0 : response.tables().size();

        List<String> findings = new ArrayList<>();
        findings.add("Table count: " + count);
        if (count > 0 && response.tables() != null) {
            findings.add("First table: " + response.tables().get(0).name());
        }

        return new DbTriageSkillResult(
                "tool",
                "Table list loaded.",
                findings,
                List.of("Pick a table for details or permissions next."),
                List.of("Want table search instead of full list?"),
                metadata().tool(),
                true,
                response
        );
    }
}
