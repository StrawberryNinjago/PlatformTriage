package com.example.smoketests.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiteSummary {
    private int passed;
    private int failed;
    private int skipped;
    private long durationMs;
    private Boolean cleanupAttempted;
}
