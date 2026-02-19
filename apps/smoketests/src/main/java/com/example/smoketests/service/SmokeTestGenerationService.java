package com.example.smoketests.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.smoketests.model.dto.GeneratedContractTest;
import com.example.smoketests.model.response.GenerateTestsPreviewResponse;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

@Service
public class SmokeTestGenerationService {

    public GenerateTestsPreviewResponse generatePreview(String specContent, boolean enforceOrder) {
        OpenAPI openApi = parseSpec(specContent);
        List<String> warnings = new ArrayList<>();
        List<GeneratedTestNode> nodes = buildNodes(openApi, warnings);

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No API operations found in the provided spec.");
        }

        if (enforceOrder) {
            applyDependencies(nodes);
            nodes = topologicalOrder(nodes, warnings);
        }

        AtomicInteger idx = new AtomicInteger(1);
        List<GeneratedContractTest> tests = nodes.stream()
                .map(node -> GeneratedContractTest.builder()
                        .testId(String.format("T%03d", idx.getAndIncrement()))
                        .name(node.name())
                        .method(node.method())
                        .path(node.path())
                        .operationId(node.operationId())
                        .expectedStatus(node.expectedStatus())
                        .dependsOn(List.copyOf(node.dependsOn()))
                        .executionOrder(node.executionOrder())
                        .build())
                .toList();

        String title = (openApi.getInfo() != null && StringUtils.hasText(openApi.getInfo().getTitle()))
                ? openApi.getInfo().getTitle()
                : "Untitled API";
        String version = (openApi.getInfo() != null && StringUtils.hasText(openApi.getInfo().getVersion()))
                ? openApi.getInfo().getVersion()
                : "unknown";

        return GenerateTestsPreviewResponse.builder()
                .title(title)
                .apiVersion(version)
                .operationCount(tests.size())
                .orderEnforced(enforceOrder)
                .tests(tests)
                .warnings(warnings)
                .generatedAt(Instant.now())
                .build();
    }

    private OpenAPI parseSpec(String specContent) {
        SwaggerParseResult result = new OpenAPIParser().readContents(specContent, null, null);
        if (result == null || result.getOpenAPI() == null) {
            String details = (result != null && result.getMessages() != null && !result.getMessages().isEmpty())
                    ? String.join("; ", result.getMessages())
                    : "Unable to parse spec content.";
            throw new IllegalArgumentException("Invalid OpenAPI/Swagger content: " + details);
        }
        return result.getOpenAPI();
    }

    private List<GeneratedTestNode> buildNodes(OpenAPI openApi, List<String> warnings) {
        Paths paths = openApi.getPaths();
        if (paths == null || paths.isEmpty()) {
            warnings.add("Spec has no paths.");
            return List.of();
        }

        List<GeneratedTestNode> nodes = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            PathItem item = pathEntry.getValue();
            if (item == null || item.readOperationsMap() == null) {
                continue;
            }

            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : item.readOperationsMap().entrySet()) {
                String method = opEntry.getKey().name().toUpperCase(Locale.ROOT);
                Operation operation = opEntry.getValue();
                if (operation == null) {
                    continue;
                }

                String operationId = StringUtils.hasText(operation.getOperationId())
                        ? operation.getOperationId()
                        : fallbackOperationId(method, path);
                String name = StringUtils.hasText(operation.getSummary())
                        ? operation.getSummary()
                        : operationId;
                int expectedStatus = pickExpectedStatus(operation.getResponses(), method);
                String key = method + " " + normalizePath(path);

                nodes.add(new GeneratedTestNode(
                        key,
                        method,
                        normalizePath(path),
                        name,
                        operationId,
                        expectedStatus,
                        new ArrayList<>(),
                        0
                ));
            }
        }
        return nodes;
    }

    private void applyDependencies(List<GeneratedTestNode> nodes) {
        List<GeneratedTestNode> postNodes = nodes.stream()
                .filter(node -> "POST".equals(node.method()))
                .toList();

        for (GeneratedTestNode node : nodes) {
            GeneratedTestNode dependency = findBestCreateDependency(node, postNodes);
            if (dependency != null && !dependency.key().equals(node.key())) {
                if (!node.dependsOn().contains(dependency.key())) {
                    node.dependsOn().add(dependency.key());
                }
            }
        }
    }

    private GeneratedTestNode findBestCreateDependency(GeneratedTestNode node, List<GeneratedTestNode> postNodes) {
        if ("POST".equals(node.method()) && segmentCount(node.path()) <= 1) {
            return null;
        }

        GeneratedTestNode best = null;
        int bestScore = -1;
        for (GeneratedTestNode post : postNodes) {
            if (post.key().equals(node.key())) {
                continue;
            }
            if (!isParentOrSame(post.path(), node.path())) {
                continue;
            }
            int score = segmentCount(post.path());
            if (score > bestScore) {
                best = post;
                bestScore = score;
            }
        }
        return best;
    }

    private List<GeneratedTestNode> topologicalOrder(List<GeneratedTestNode> nodes, List<String> warnings) {
        Map<String, GeneratedTestNode> byKey = nodes.stream()
                .collect(Collectors.toMap(GeneratedTestNode::key, n -> n, (a, b) -> a, LinkedHashMap::new));

        Map<String, Integer> originalIndex = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            originalIndex.put(nodes.get(i).key(), i);
        }

        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (GeneratedTestNode node : nodes) {
            indegree.put(node.key(), 0);
            adjacency.put(node.key(), new LinkedHashSet<>());
        }

        for (GeneratedTestNode node : nodes) {
            List<String> missingDeps = new ArrayList<>();
            for (String dep : List.copyOf(node.dependsOn())) {
                if (!indegree.containsKey(dep)) {
                    missingDeps.add(dep);
                    continue;
                }
                if (adjacency.get(dep).add(node.key())) {
                    indegree.put(node.key(), indegree.get(node.key()) + 1);
                }
            }
            if (!missingDeps.isEmpty()) {
                warnings.add("Ignored missing dependency for " + node.key() + ": " + String.join(", ", missingDeps));
                node.dependsOn().removeAll(missingDeps);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .sorted(Comparator.comparingInt(e -> originalIndex.get(e.getKey())))
                .forEach(e -> queue.add(e.getKey()));

        List<GeneratedTestNode> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String key = queue.poll();
            GeneratedTestNode node = byKey.get(key);
            if (node == null) {
                continue;
            }
            node.setExecutionOrder(ordered.size() + 1);
            ordered.add(node);

            for (String next : adjacency.getOrDefault(key, Set.of())) {
                int updated = indegree.get(next) - 1;
                indegree.put(next, updated);
                if (updated == 0) {
                    queue.add(next);
                }
            }
        }

        if (ordered.size() != nodes.size()) {
            warnings.add("Dependency cycle detected. Falling back to stable order for unresolved operations.");
            nodes.stream()
                    .filter(node -> ordered.stream().noneMatch(o -> Objects.equals(o.key(), node.key())))
                    .sorted(Comparator.comparingInt(node -> originalIndex.get(node.key())))
                    .forEach(node -> {
                        node.setExecutionOrder(ordered.size() + 1);
                        ordered.add(node);
                    });
        }

        return ordered;
    }

    private int pickExpectedStatus(ApiResponses responses, String method) {
        if (responses != null && !responses.isEmpty()) {
            return responses.keySet().stream()
                    .filter(code -> code != null && code.matches("2\\d\\d"))
                    .map(Integer::parseInt)
                    .sorted()
                    .findFirst()
                    .orElse(defaultStatusForMethod(method));
        }
        return defaultStatusForMethod(method);
    }

    private int defaultStatusForMethod(String method) {
        return switch (method) {
            case "POST" -> 201;
            case "DELETE" -> 204;
            default -> 200;
        };
    }

    private String fallbackOperationId(String method, String path) {
        String normalized = normalizePath(path).replaceAll("[^a-zA-Z0-9/]", "");
        String collapsed = normalized.replace("/", "_");
        while (collapsed.startsWith("_")) {
            collapsed = collapsed.substring(1);
        }
        return (method.toLowerCase(Locale.ROOT) + "_" + collapsed).replaceAll("__+", "_");
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path.trim())) {
            return "/";
        }
        String normalized = path.trim().replaceAll("/+", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isParentOrSame(String parentPath, String targetPath) {
        String parent = normalizePath(parentPath);
        String target = normalizePath(targetPath);
        return target.equals(parent) || target.startsWith(parent + "/");
    }

    private int segmentCount(String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized)) {
            return 0;
        }
        return (int) normalized.chars().filter(ch -> ch == '/').count();
    }

    private static class GeneratedTestNode {
        private final String key;
        private final String method;
        private final String path;
        private final String name;
        private final String operationId;
        private final int expectedStatus;
        private final List<String> dependsOn;
        private int executionOrder;

        private GeneratedTestNode(
                String key,
                String method,
                String path,
                String name,
                String operationId,
                int expectedStatus,
                List<String> dependsOn,
                int executionOrder
        ) {
            this.key = key;
            this.method = method;
            this.path = path;
            this.name = name;
            this.operationId = operationId;
            this.expectedStatus = expectedStatus;
            this.dependsOn = dependsOn;
            this.executionOrder = executionOrder;
        }

        private String key() {
            return key;
        }

        private String method() {
            return method;
        }

        private String path() {
            return path;
        }

        private String name() {
            return name;
        }

        private String operationId() {
            return operationId;
        }

        private int expectedStatus() {
            return expectedStatus;
        }

        private List<String> dependsOn() {
            return dependsOn;
        }

        private int executionOrder() {
            return executionOrder;
        }

        private void setExecutionOrder(int executionOrder) {
            this.executionOrder = executionOrder;
        }
    }
}
