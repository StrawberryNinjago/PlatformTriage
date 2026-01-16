package com.example.smoketests.model.dto;

import java.util.Map;

import com.example.smoketests.model.enums.Suite;
import com.example.smoketests.model.enums.TestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {
    private Suite suite;
    private String name;
    private String operationId;
    private Map<String, Object> expected;
    private Map<String, Object> actual;
    private TestStatus status;
    private long durationMs;
    private String evidenceRef;
}
