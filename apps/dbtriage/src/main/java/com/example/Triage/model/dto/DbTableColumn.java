package com.example.Triage.model.dto;

public record DbTableColumn(
        String name,
        int ordinalPosition,
        String dataType,
        boolean nullable,
        String columnDefault
) {
}
