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
public class GeneratedContractTest {
    private String testId;
    private String name;
    private String method;
    private String path;
    private String operationId;
    private int expectedStatus;
    private List<String> dependsOn;
    private int executionOrder;
}
