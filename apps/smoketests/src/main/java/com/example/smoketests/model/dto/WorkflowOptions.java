package com.example.smoketests.model.dto;

import com.example.smoketests.model.enums.WorkflowSource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowOptions {
    private WorkflowSource source;
    private String workflowId;
    private String workflowUploadId;
    private boolean alwaysAttemptCleanup;
}
