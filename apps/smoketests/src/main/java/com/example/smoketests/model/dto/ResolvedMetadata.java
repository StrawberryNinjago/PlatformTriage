package com.example.smoketests.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedMetadata {
    private String specFingerprint;
    private String generatedTestSetId;
    private String workflowFingerprint;
    private String workflowId;
}
