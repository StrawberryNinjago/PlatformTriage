package com.example.smoketests.model.dto;

import java.time.Instant;

import com.example.smoketests.model.enums.CacheStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationInfo {
    private CacheStatus cacheStatus;
    private String generatedTestSetId;
    private Instant generatedAt;
}
