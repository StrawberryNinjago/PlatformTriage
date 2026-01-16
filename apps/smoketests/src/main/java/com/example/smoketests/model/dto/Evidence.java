package com.example.smoketests.model.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {
    private RequestInfo request;
    private ResponseInfo response;
    private TraceInfo trace;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestInfo {
        private String method;
        private String url;
        private Map<String, String> headers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseInfo {
        private int status;
        private Map<String, String> headers;
        private String bodyPreview;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceInfo {
        private String traceId;
        private String spanId;
    }
}
