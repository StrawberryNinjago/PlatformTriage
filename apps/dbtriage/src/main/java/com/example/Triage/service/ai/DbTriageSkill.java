package com.example.Triage.service.ai;

public interface DbTriageSkill {
    DbTriageSkillMetadata metadata();

    DbTriageSkillResult execute(DbTriageSkillContext context) throws Exception;
}
