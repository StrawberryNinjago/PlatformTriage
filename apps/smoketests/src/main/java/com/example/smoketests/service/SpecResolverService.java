package com.example.smoketests.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.smoketests.model.dto.GenerationInfo;
import com.example.smoketests.model.dto.SpecInfo;
import com.example.smoketests.model.dto.SpecSource;
import com.example.smoketests.model.dto.Target;
import com.example.smoketests.model.enums.CacheStatus;
import com.example.smoketests.model.response.SpecResolveResponse;

/**
 * Service for resolving and managing OpenAPI specifications
 */
@Service
public class SpecResolverService {

    private static final Logger log = LoggerFactory.getLogger(SpecResolverService.class);

    /**
     * Resolve spec from blob storage or upload Compute fingerprint and check
     * cache status
     */
    public SpecResolveResponse resolveSpec(String environment, String capability,
            String apiVersion, SpecSource source) {
        log.info("Resolving spec for {}/{}/{}", environment, capability, apiVersion);

        // TODO: Implement actual spec fetching from blob storage or upload
        // For now, return mock data
        String baseUrl = buildBaseUrl(environment, capability, apiVersion);
        String fingerprint = computeFingerprint(source);

        Target target = Target.builder()
                .environment(environment)
                .capability(capability)
                .apiVersion(apiVersion)
                .baseUrl(baseUrl)
                .build();

        SpecInfo specInfo = SpecInfo.builder()
                .source(source)
                .fingerprint(fingerprint)
                .contentType("application/yaml")
                .retrievedAt(Instant.now())
                .build();

        GenerationInfo generationInfo = GenerationInfo.builder()
                .cacheStatus(CacheStatus.PRESENT)
                .generatedTestSetId("gts_" + fingerprint.substring(0, 8))
                .generatedAt(Instant.now())
                .build();

        log.info("Spec resolved: fingerprint={}", fingerprint);

        return SpecResolveResponse.builder()
                .target(target)
                .spec(specInfo)
                .generation(generationInfo)
                .build();
    }

    /**
     * Compute fingerprint (ETag or SHA256) for spec
     */
    private String computeFingerprint(SpecSource source) {
        // TODO: Implement actual fingerprint computation
        // Should hash the spec content
        return "etag:w/\"5f9a2b3c4d5e6f7g8h9i0j1k2\"";
    }

    /**
     * Build base URL from environment, capability, and version
     */
    private String buildBaseUrl(String environment, String capability, String apiVersion) {
        String base = switch (environment) {
            case "local" ->
                "http://localhost:8081";
            case "dev" ->
                "https://capability.dev.att.com";
            case "test" ->
                "https://capability.test.att.com";
            case "stage" ->
                "https://capability.stage.att.com";
            default ->
                "http://localhost:8081";
        };

        String version = (apiVersion != null && !apiVersion.isEmpty()) ? "/" + apiVersion : "";
        return base + "/" + capability + version;
    }
}
