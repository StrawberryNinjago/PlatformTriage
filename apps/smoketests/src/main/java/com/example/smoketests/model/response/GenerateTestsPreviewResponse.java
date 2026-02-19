package com.example.smoketests.model.response;

import java.time.Instant;
import java.util.List;

import com.example.smoketests.model.dto.GeneratedContractTest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTestsPreviewResponse {
    private String title;
    private String apiVersion;
    private int operationCount;
    private boolean orderEnforced;
    private List<GeneratedContractTest> tests;
    private List<String> warnings;
    private Instant generatedAt;
}
