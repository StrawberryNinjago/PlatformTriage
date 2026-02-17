package com.example.platformtriage.service.ai;

public interface PlatformTriageSkill {
    PlatformTriageSkillMetadata metadata();

    PlatformTriageSkillResult execute(PlatformTriageSkillContext context) throws Exception;
}
