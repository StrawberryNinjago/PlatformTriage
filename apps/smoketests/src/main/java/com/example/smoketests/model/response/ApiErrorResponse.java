package com.example.smoketests.model.response;

import java.util.Map;

import com.example.smoketests.model.enums.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private ErrorInfo error;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private ErrorCode code;
        private String message;
        private Map<String, String> details;
    }
}
