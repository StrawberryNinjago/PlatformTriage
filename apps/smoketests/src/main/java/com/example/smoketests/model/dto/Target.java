package com.example.smoketests.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Target {
    private String environment;
    private String capability;
    private String apiVersion;
    private String baseUrl;
}
