package com.example.smoketests.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {
    private String workflowId;
    private String name;
    private int version;
    private String description;
    private List<String> steps;
}
