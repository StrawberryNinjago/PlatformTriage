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
public class ValidateConfigRequest {
    @NotNull
    private Target target;
    
    @NotNull
    private Spec spec;
    
    @NotNull
    private Auth auth;
    
    @NotNull
    private SuiteConfig suiteConfig;
}
