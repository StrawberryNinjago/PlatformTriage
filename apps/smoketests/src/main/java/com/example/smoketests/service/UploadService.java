package com.example.smoketests.service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.smoketests.model.response.UploadResponse;

/**
 * Service for handling file uploads (OpenAPI specs and workflow YAML)
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    /**
     * Handle file upload and store temporarily
     */
    public UploadResponse uploadFile(MultipartFile file, String purpose) {
        log.info("Processing file upload: {} for purpose: {}",
                file.getOriginalFilename(), purpose);

        try {
            // Validate file
            validateFile(file, purpose);

            // Generate upload ID
            String uploadId = generateUploadId();

            // Compute SHA256
            String sha256 = computeSha256(file);

            // TODO: Store file to filesystem or temporary storage
            // Path: ~/.triage/smoketests/uploads/{uploadId}
            log.info("File uploaded successfully: {}", uploadId);

            return UploadResponse.builder()
                    .uploadId(uploadId)
                    .purpose(purpose)
                    .fileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .sha256(sha256)
                    .createdAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file, String purpose) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size (max 10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed (10MB)");
        }

        // Validate content type based on purpose
        String contentType = file.getContentType();
        if ("OPENAPI_SPEC".equals(purpose) || "WORKFLOW_YAML".equals(purpose)) {
            if (contentType != null
                    && !contentType.contains("yaml")
                    && !contentType.contains("yml")
                    && !contentType.contains("json")) {
                log.warn("Unexpected content type for {}: {}", purpose, contentType);
            }
        }
    }

    /**
     * Generate unique upload ID
     */
    private String generateUploadId() {
        return "upl_" + System.currentTimeMillis() + "_"
                + Long.toHexString(System.nanoTime());
    }

    /**
     * Compute SHA256 hash of file content
     */
    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Failed to compute SHA256: {}", e.getMessage());
            return "unavailable";
        }
    }

    /**
     * Get uploaded file content by upload ID
     */
    public String getUploadContent(String uploadId) {
        log.info("Retrieving upload content: {}", uploadId);

        // TODO: Load from filesystem
        // For now, return empty
        throw new UnsupportedOperationException("Upload retrieval not yet implemented");
    }
}
