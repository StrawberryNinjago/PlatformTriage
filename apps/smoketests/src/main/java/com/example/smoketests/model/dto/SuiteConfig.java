package com.example.smoketests.model.dto;

import com.example.smoketests.model.enums.Suite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiteConfig {
    private Suite suite;
    private ContractOptions contractOptions;
    private WorkflowOptions workflowOptions;
}
