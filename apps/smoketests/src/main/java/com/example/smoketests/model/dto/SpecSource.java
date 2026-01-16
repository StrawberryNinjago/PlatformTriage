package com.example.smoketests.model.dto;

import com.example.smoketests.model.enums.SpecSourceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecSource {
    private SpecSourceType type;
    private String blobPath;
    private String uploadId;
}
