package com.example.Triage.service.ai.skill;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.Triage.handler.DbConnectionHandler;
import com.example.Triage.model.response.DbIdentityResponse;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VerifyConnectionSkill implements DbTriageSkill {

    private final DbConnectionHandler connectionHandler;

    @Override
    public DbTriageSkillMetadata metadata() {
        return new DbTriageSkillMetadata(
                DbTriageTools.VERIFY_CONNECTION,
                "Validate connection and return database identity metadata.",
                true
        );
    }

    @Override
    public DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception {
        DbIdentityResponse identity = connectionHandler.getIdentity(context.connectionId());

        return new DbTriageSkillResult(
                "tool",
                "Connection identity confirmed.",
                List.of(
                        "Database: " + identity.database(),
                        "Current user: " + identity.currentUser(),
                        "PostgreSQL version: " + identity.serverVersion(),
                        "Schema: " + identity.schema()
                ),
                List.of("Run Flyway health or list tables next."),
                List.of("Do you want me to check permissions for a specific table?"),
                metadata().tool(),
                true,
                identity
        );
    }
}
