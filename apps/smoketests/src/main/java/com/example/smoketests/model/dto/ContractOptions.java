package com.example.smoketests.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractOptions {
    private boolean happyPaths;
    private boolean negativeAuth;
    private boolean basic400;
    private boolean strictSchema;
    private boolean failFast;
    private LimitEndpointsConfig limitEndpoints;
}
