package com.example.smoketests.model.response;

import java.util.List;

import com.example.smoketests.model.dto.WorkflowDefinition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowCatalogResponse {
    private String capability;
    private List<WorkflowDefinition> workflows;
}
