package com.example.Triage.service.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DbTriageSkillRegistry {

    private final Map<String, DbTriageSkill> skillByName;

    public DbTriageSkillRegistry(List<DbTriageSkill> skills) {
        this.skillByName = skills.stream()
                .collect(Collectors.toMap(
                        skill -> skill.metadata().tool(),
                        skill -> skill,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        log.info("Registered {} DB triage skills: {}", this.skillByName.size(), this.skillByName.keySet());
    }

    public Optional<DbTriageSkill> resolve(String tool) {
        return Optional.ofNullable(skillByName.get(tool));
    }

    public List<DbTriageSkillMetadata> listMetadata() {
        return new ArrayList<>(skillByName.values().stream()
                .map(DbTriageSkill::metadata)
                .toList());
    }

    public List<String> supportedToolNames() {
        return new ArrayList<>(skillByName.keySet());
    }

    public boolean isSupported(String tool) {
        return skillByName.containsKey(tool);
    }
}
