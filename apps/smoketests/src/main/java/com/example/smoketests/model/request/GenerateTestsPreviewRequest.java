package com.example.smoketests.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTestsPreviewRequest {
    @NotBlank
    private String specContent;

    @Builder.Default
    private boolean enforceOrder = true;
}
