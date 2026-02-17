package com.example.Triage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.Triage.handler.DbPrivilegesHandler;
import com.example.Triage.model.response.DbPrivilegesResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CheckPrivilegesSkill implements DbTriageSkill {

    private final DbPrivilegesHandler privilegesHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.CHECK_PRIVILEGES,
                "Check table access and missing privileges.",
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
                    "I can check permissions, but I need a table name.",
                    List.of("Missing table name."),
                    List.of("Try: do I have permission for cart_item?"),
                    List.of("Which table should I check?"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        DbPrivilegesResponse response =
                privilegesHandler.checkTablePrivileges(context.connectionId(), context.parameter("schema"), tableName);

        List<String> findings = new ArrayList<>();
        findings.add("Table: " + context.parameter("schema") + "." + tableName);
        findings.add("Status: " + response.status());
        findings.add("Granted privileges: " + (response.grantedPrivileges() == null ? 0 : response.grantedPrivileges().size()));
        if (response.missingPrivileges() != null && !response.missingPrivileges().isEmpty()) {
            findings.add("Missing privileges: " + response.missingPrivileges().size());
        }

        List<String> next = new ArrayList<>();
        next.add(response.missingPrivileges() == null || response.missingPrivileges().isEmpty()
                ? "Privileges look sufficient for the checked object."
                : "Request missing grants from DB admin before running mutation queries.");

        return new DbTriageSkillResult(
                "tool",
                "Privilege check executed.",
                findings,
                next,
                List.of("Need index/constraint checks after this table?"),
                metadata().tool(),
                true,
                response
        );
    }
}
