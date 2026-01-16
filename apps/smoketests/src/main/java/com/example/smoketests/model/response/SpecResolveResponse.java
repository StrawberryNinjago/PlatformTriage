package com.example.smoketests.model.response;

import com.example.smoketests.model.dto.GenerationInfo;
import com.example.smoketests.model.dto.SpecInfo;
import com.example.smoketests.model.dto.Target;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecResolveResponse {
    private Target target;
    private SpecInfo spec;
    private GenerationInfo generation;
}
