package com.example.Triage.model.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record DbIndex(
        String table, // nullable when table context is implicit
        String name,
        boolean unique,
        boolean primary,
        String accessMethod, // btree, gin, gist, hash
        List<String> columns, // best-effort for simple cases
        String definition // pg_get_indexdef
) {
}
