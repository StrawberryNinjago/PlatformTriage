package com.example.platformtriage.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PlatformTriageSkillRegistry {

    private final Map<String, PlatformTriageSkill> skillByName;

    public PlatformTriageSkillRegistry(List<PlatformTriageSkill> skills) {
        this.skillByName = skills.stream()
                .collect(java.util.stream.Collectors.toMap(
                        skill -> skill.metadata().tool(),
                        skill -> skill,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        log.info("Registered {} platform triage skills: {}", this.skillByName.size(), this.skillByName.keySet());
    }

    public Optional<PlatformTriageSkill> resolve(String tool) {
        return Optional.ofNullable(skillByName.get(tool));
    }

    public List<String> supportedToolNames() {
        return new ArrayList<>(skillByName.keySet());
    }

    public boolean isSupported(String tool) {
        return skillByName.containsKey(tool);
    }
}
