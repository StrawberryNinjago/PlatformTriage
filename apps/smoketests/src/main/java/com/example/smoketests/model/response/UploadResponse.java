package com.example.smoketests.model.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String uploadId;
    private String purpose;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private Instant createdAt;
}
