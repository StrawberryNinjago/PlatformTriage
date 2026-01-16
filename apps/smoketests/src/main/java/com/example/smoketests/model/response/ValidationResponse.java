package com.example.smoketests.model.response;

import java.util.List;

import com.example.smoketests.model.dto.ResolvedMetadata;
import com.example.smoketests.model.dto.ValidationCheck;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {
    private boolean ok;
    private String summary;
    private ResolvedMetadata resolved;
    private List<ValidationCheck> checks;
}
