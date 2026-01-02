package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record NextAction(String label, String target) {
}
