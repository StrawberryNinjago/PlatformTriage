package com.example.smoketests.model.dto;

import com.example.smoketests.model.enums.CheckStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationCheck {
    private String name;
    private CheckStatus status;
    private String details;
}
