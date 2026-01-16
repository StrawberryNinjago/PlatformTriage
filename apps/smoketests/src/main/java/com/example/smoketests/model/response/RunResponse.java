package com.example.smoketests.model.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.example.smoketests.model.dto.ResolvedMetadata;
import com.example.smoketests.model.dto.RunSummary;
import com.example.smoketests.model.dto.TestResult;
import com.example.smoketests.model.enums.RunStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunResponse {
    private String runId;
    private RunStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private ResolvedMetadata resolved;
    private RunSummary summary;
    private List<TestResult> results;
    private Map<String, String> links;
}
