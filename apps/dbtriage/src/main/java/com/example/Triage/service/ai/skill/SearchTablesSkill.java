package com.example.Triage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.Triage.handler.DbTablesHandler;
import com.example.Triage.model.response.DbTableSearchResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchTablesSkill implements DbTriageSkill {

    private final DbTablesHandler tablesHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.SEARCH_TABLES,
                "Search tables by pattern and return matches.",
                true,
                List.of("searchQuery")
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        String query = context.parameter("searchQuery");
        if (!StringUtils.hasText(query)) {
            return new DbTriageSkillResult(
                    "clarify",
                    "I can search tables, but I need a search query.",
                    List.of("Search query missing."),
                    List.of("Try: find tables for cart"),
                    List.of("What table name pattern should I search for?"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        DbTableSearchResponse response = tablesHandler.searchTables(
                context.connectionId(),
                context.parameter("schema"),
                query
        );
        List<String> findings = new ArrayList<>();
        findings.add("Search results: " + response.tables().size());

        return new DbTriageSkillResult(
                "tool",
                "Table search executed.",
                findings,
                List.of("Open table details by name from the results."),
                List.of("Need another search pattern?"),
                metadata().tool(),
                true,
                response
        );
    }
}
