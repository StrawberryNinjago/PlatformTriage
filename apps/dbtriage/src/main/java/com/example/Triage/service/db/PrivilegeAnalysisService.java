package com.example.Triage.service.db;

import com.example.Triage.model.dto.EnvironmentCapabilityMatrix;
import com.example.Triage.model.dto.PrivilegeRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to analyze missing privileges and generate grant snippets
 */
@Service
@Slf4j
public class PrivilegeAnalysisService {

    public List<PrivilegeRequirement> analyzeMissingPrivileges(EnvironmentCapabilityMatrix capabilities) {
        List<PrivilegeRequirement> requirements = new ArrayList<>();

        if (!capabilities.columns().available()) {
            requirements.add(new PrivilegeRequirement(
                    "Columns",
                    "SELECT on information_schema.columns",
                    capabilities.columns().message(),
                    List.of("GRANT SELECT ON information_schema.columns TO " + extractUsername(capabilities.connectionId()))
            ));
        }

        if (!capabilities.constraints().available()) {
            requirements.add(new PrivilegeRequirement(
                    "Constraints",
                    "SELECT on information_schema.table_constraints",
                    capabilities.constraints().message(),
                    List.of("GRANT SELECT ON information_schema.table_constraints TO " + extractUsername(capabilities.connectionId()))
            ));
        }

        if (!capabilities.indexes().available()) {
            requirements.add(new PrivilegeRequirement(
                    "Indexes",
                    "SELECT on pg_catalog.pg_indexes",
                    capabilities.indexes().message(),
                    List.of("GRANT SELECT ON pg_catalog.pg_indexes TO " + extractUsername(capabilities.connectionId()))
            ));
        }

        if (!capabilities.flywayHistory().available()) {
            requirements.add(new PrivilegeRequirement(
                    "Flyway History",
                    "SELECT on flyway_schema_history table",
                    capabilities.flywayHistory().message(),
                    List.of("GRANT SELECT ON public.flyway_schema_history TO " + extractUsername(capabilities.connectionId()))
            ));
        }

        return requirements;
    }

    public String generatePrivilegeRequestSnippet(List<PrivilegeRequirement> requirements, String targetEnv) {
        if (requirements.isEmpty()) {
            return null;
        }

        StringBuilder snippet = new StringBuilder();
        snippet.append("-- Privilege Request for Environment Comparison\n");
        snippet.append("-- Target Environment: ").append(targetEnv).append("\n");
        snippet.append("-- Purpose: Enable full schema drift detection\n\n");
        snippet.append("-- These grants provide read-only metadata access\n");
        snippet.append("-- No data access is granted\n\n");

        snippet.append("-- Grant read access to information_schema (standard SQL catalog)\n");
        snippet.append("-- Note: information_schema is typically readable by default, but may be restricted\n");
        snippet.append("-- GRANT SELECT ON ALL TABLES IN SCHEMA information_schema TO <your_user>;\n\n");

        snippet.append("-- Grant read access to pg_catalog (PostgreSQL system catalog)\n");
        snippet.append("GRANT SELECT ON pg_catalog.pg_indexes TO <your_user>;\n");
        snippet.append("GRANT SELECT ON pg_catalog.pg_constraint TO <your_user>;\n");
        snippet.append("GRANT SELECT ON pg_catalog.pg_class TO <your_user>;\n\n");

        snippet.append("-- Optional: Grant access to Flyway history table (if Flyway is in use)\n");
        snippet.append("GRANT SELECT ON public.flyway_schema_history TO <your_user>;\n\n");

        snippet.append("-- Minimal privilege set (if above is too broad):\n");
        for (PrivilegeRequirement req : requirements) {
            snippet.append("-- ").append(req.capability()).append(": ").append(req.reason()).append("\n");
            for (String grant : req.requiredGrants()) {
                snippet.append(grant.replace(extractUsername("dummy"), "<your_user>")).append(";\n");
            }
            snippet.append("\n");
        }

        snippet.append("-- After applying grants, reconnect and re-run comparison\n");

        return snippet.toString();
    }

    private String extractUsername(String connectionId) {
        // Placeholder - in real implementation, extract from connection details
        return "<user>";
    }
}

