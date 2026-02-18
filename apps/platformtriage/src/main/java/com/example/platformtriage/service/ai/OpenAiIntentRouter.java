package com.example.platformtriage.service.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiIntentRouter {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Optional<AiIntent> route(String question, String contextSummaryHint) {
        String apiKey = resolveEnv("OPENAI_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }

        String baseUrl = resolveEnv("OPENAI_BASE_URL", "https://api.openai.com/v1");
        String model = resolveEnv("OPENAI_MODEL", "gpt-4.1-mini");
        String endpoint = baseUrl.endsWith("/chat/completions") ? baseUrl : String.format("%s/chat/completions", baseUrl);
        String requestBody = buildRequestBody(model, question, contextSummaryHint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI routing failed with status {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            return parse(response.body());
        } catch (IOException | InterruptedException e) {
            log.warn("OpenAI routing failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AiIntent> parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            JsonNode content = choice.path("message").path("content");
            String text = stripJsonFence(content.asText(""));
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }

            JsonNode node = objectMapper.readTree(text);
            String tool = node.path("tool").asText("chat");
            double confidence = node.path("confidence").asDouble(0.45d);
            JsonNode paramsNode = node.path("params");

            Map<String, String> params = new HashMap<>();
            if (paramsNode != null && paramsNode.isObject()) {
                paramsNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue() != null && !entry.getValue().isNull()) {
                        params.put(entry.getKey(), entry.getValue().asText().trim());
                    }
                });
            }

            return Optional.of(AiIntent.from(tool, confidence, params));
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI routing response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildRequestBody(String model, String question, String contextSummaryHint) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.1d);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content",
                """
                You are an intent router for Deployment/Platform triage tools.
                Map each user message to exactly one tool name and optional params JSON.
                If no tool is needed, return tool="chat".

                Allowed tool names:
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s

                Use this JSON format and nothing else:
                {
                  "tool": "...",
                  "confidence": 0.0,
                  "params": {
                    "namespace":"...", "selector":"...", "release":"...",
                    "podName":"...", "logLines":"...", "traceId":"...", "query":"...",
                    "findingCode":"...", "limitEvents":"...",
                    "warningsOnly":"..."
                  }
                }

                For namespace lookup, extract namespace/release/selector when present in user text.
                Prefer tool detection from user intent phrases over assumptions.
                """.formatted(
                PlatformTriageTools.LOAD_SUMMARY,
                PlatformTriageTools.SUMMARIZE,
                PlatformTriageTools.LIST_PODS,
                PlatformTriageTools.GET_POD_LOGS,
                PlatformTriageTools.LIST_EVENTS,
                PlatformTriageTools.LIST_SERVICES,
                PlatformTriageTools.CHECK_VERSIONS,
                PlatformTriageTools.TRACE_SEARCH,
                PlatformTriageTools.PRIMARY_FAILURE,
                PlatformTriageTools.TOP_WARNING,
                PlatformTriageTools.FINDING_DETAILS
                )
        ));
        messages.add(Map.of(
                "role", "user",
                "content", "Context hint: " + (contextSummaryHint == null ? "<none>" : contextSummaryHint)
                        + "\nUser question: " + question
        ));

        body.put("messages", messages);
        body.put("response_format", Map.of("type", "json_object"));

        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OpenAI request body", e);
        }
    }

    private String stripJsonFence(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```\\s*$", "");
        }
        return trimmed;
    }

    private String resolveEnv(String key) {
        return resolveEnv(key, null);
    }

    private String resolveEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
