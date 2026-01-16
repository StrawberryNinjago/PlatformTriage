package com.example.smoketests.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunSummary {
    private SuiteSummary contract;
    private SuiteSummary workflow;
    private String topFinding;
}
