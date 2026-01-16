package com.example.smoketests.model.request;

import com.example.smoketests.model.dto.Auth;
import com.example.smoketests.model.dto.Spec;
import com.example.smoketests.model.dto.SuiteConfig;
import com.example.smoketests.model.dto.Target;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunSmokeTestsRequest {
    @NotNull
    private Target target;
    
    @NotNull
    private Spec spec;
    
    @NotNull
    private Auth auth;
    
    @NotNull
    private SuiteConfig suiteConfig;
    
    private ExecutionConfig execution;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionConfig {
        private String onStart; // "LOAD_CACHED_OR_GENERATE"
        private Long timeoutMs;
    }
}
