package com.example.Triage.model.dto;

public record TableInfo(
                String name,
                Long estimatedRowCount,
                String owner) {
}
