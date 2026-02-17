package com.example.platformtriage.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.request.AiTriageRequest;
import com.example.platformtriage.model.enums.FailureCode;
import com.example.platformtriage.model.response.AiTriageResponse;
import com.example.platformtriage.model.response.DeploymentSummaryResponse;
import com.example.platformtriage.service.ai.AiIntent;
import com.example.platformtriage.service.ai.OpenAiIntentRouter;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillRegistry;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTriageService {

    private static final double OPENAI_MIN_CONFIDENCE = 0.68d;
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("(?i)\\bnamespace\\s*[:=]?\\s*([a-z0-9.-]+)");
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("(?i)\\b(?:selector|label selector)\\s*[:=]?\\s*([a-zA-Z0-9_.:\\-]+=[-_a-zA-Z0-9/.]+(?:,\\s*[a-zA-Z0-9_.:\\-]+=[-_a-zA-Z0-9/.]+)*)");
    private static final Pattern RELEASE_PATTERN = Pattern.compile("(?i)\\brelease\\s*[:=]?\\s*([\\w.-]+)");
    private static final Pattern LIMIT_EVENTS_PATTERN = Pattern.compile("(?i)\\blimit\\s*events\\s*[:=]?\\s*(\\d+)");
    private static final Pattern LOG_LINES_PATTERN = Pattern.compile("(?i)\\b(?:last\\s+)?(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+(?:lines?|line)\\s+(?:of\\s+)?logs\\b");
    private static final Pattern FINDING_CODE_PATTERN = Pattern.compile("(?i)\\b([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\\b");

    private final OpenAiIntentRouter openAiIntentRouter;
    private final PlatformTriageSkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    public AiTriageResponse triage(AiTriageRequest request) {
        String question = normalize(request.question());
        Map<String, Object> rawContext = request.context() == null ? Map.of() : request.context();

        DeploymentSummaryResponse summary = resolveSummary(rawContext);
        AiIntent intent = resolveIntent(question, summary, rawContext);

        PlatformTriageSkillContext context = buildSkillContext(intent, question, rawContext, summary);
        return executeTool(intent, context);
    }

    private AiTriageResponse executeTool(AiIntent intent, PlatformTriageSkillContext context) {
        Optional<PlatformTriageSkill> skillOpt = skillRegistry.resolve(intent.tool());
        if (skillOpt.isEmpty()) {
            return summarizeWithoutAction(context);
        }

        PlatformTriageSkill skill = skillOpt.get();
        try {
            PlatformTriageSkillResult result = skill.execute(context);
            if (result == null) {
                return summarizeWithoutAction(context);
            }
            return result.toResponse();
        } catch (Exception e) {
            log.error("Platform triage skill execution failed", e);
            return new AiTriageResponse(
                    "tool_error",
                    "I recognized the request but could not execute it: " + e.getMessage(),
                    List.of("Tool execution failed for " + intent.tool()),
                    List.of("Retry with a clearer request (namespace and selector/release)."),
                    List.of("Need one of these: load summary, summarize, primary issue, risks."),
                    intent.tool(),
                    false,
                    null
            );
        }
    }

    private PlatformTriageSkillContext buildSkillContext(
            AiIntent intent,
            String question,
            Map<String, Object> rawContext,
            DeploymentSummaryResponse summary
    ) {
        Map<String, String> params = resolveToolParameters(intent, question, rawContext, summary);
        return new PlatformTriageSkillContext(
                question,
                params.getOrDefault("namespace", ""),
                params.get("selector"),
                params.get("release"),
                parseLimitEvents(params.get("limitEvents")),
                summary,
                intent,
                params
        );
    }

    private AiIntent resolveIntent(String question, DeploymentSummaryResponse summary, Map<String, Object> rawContext) {
        AiIntent heuristic = heuristicIntent(question, summary);
        if (!"chat".equals(heuristic.tool())) {
            return heuristic;
        }

        String contextHint = buildContextHint(rawContext, summary);
        Optional<AiIntent> aiIntent = openAiIntentRouter.route(question, contextHint);
        if (aiIntent.isPresent()) {
            AiIntent parsed = aiIntent.get();
            if (!"chat".equals(parsed.tool()) && skillRegistry.isSupported(parsed.tool())
                    && parsed.confidence() >= OPENAI_MIN_CONFIDENCE) {
                return parsed;
            }
        }

        return heuristic;
    }

    private Map<String, String> resolveToolParameters(
            AiIntent intent,
            String question,
            Map<String, Object> rawContext,
            DeploymentSummaryResponse summary
    ) {
        Map<String, String> params = new HashMap<>();
        if (intent.parameters() != null) {
            params.putAll(intent.parameters());
        }

        String namespace = pickParamOrFromContext(params, "namespace", summary, question, "namespace");
        if (StringUtils.hasText(namespace)) {
            params.put("namespace", namespace);
        }
        String selector = pickParamOrFromContext(params, "selector", summary, question, "selector");
        if (StringUtils.hasText(selector)) {
            params.put("selector", selector);
        }
        String release = pickParamOrFromContext(params, "release", summary, question, "release");
        if (StringUtils.hasText(release)) {
            params.put("release", release);
        }
        String limitEvents = pickLimitFromText(question);
        if (!StringUtils.hasText(limitEvents)) {
            limitEvents = pickString(rawContext, "limitEvents");
        }
        if (StringUtils.hasText(limitEvents)) {
            params.put("limitEvents", limitEvents);
        }

        String logLines = pickLogLines(question);
        if (!StringUtils.hasText(logLines)) {
            logLines = pickString(rawContext, "logLines");
        }
        if (StringUtils.hasText(logLines)) {
            params.put("logLines", logLines);
        }

        String podName = pickPodNameFromQuestion(question, summary);
        if (StringUtils.hasText(podName)) {
            params.put("podName", podName);
        }

        if ("finding_details".equals(intent.tool()) && StringUtils.hasText(pickParamOrFromContext(params, "findingCode", summary, question, "findingCode"))) {
            params.put("findingCode", pickParamOrFromContext(params, "findingCode", summary, question, "findingCode"));
        }

        return params;
    }

    private String pickParamOrFromContext(
            Map<String, String> params,
            String key,
            DeploymentSummaryResponse summary,
            String question,
            String preferred
    ) {
        String fromParams = params.get(key);
        if (StringUtils.hasText(fromParams)) {
            return fromParams;
        }

        String extracted = switch (preferred) {
            case "namespace" -> extractNamespace(question);
            case "selector" -> extractSelector(question);
            case "release" -> extractRelease(question);
            case "findingCode" -> extractFindingCode(question);
            case "limitEvents" -> pickLimitFromText(question);
            case "logLines" -> pickLogLines(question);
            default -> null;
        };
        if (StringUtils.hasText(extracted)) {
            return extracted;
        }

        if (summary != null && summary.target() != null) {
            return switch (preferred) {
                case "namespace" -> summary.target().namespace();
                case "selector" -> summary.target().selector();
                case "release" -> summary.target().release();
                default -> null;
            };
        }

        return null;
    }

    private String pickString(Map<String, Object> context, String key) {
        Object value = context == null ? null : context.get(key);
        if (value == null) {
            return null;
        }
        String asString = String.valueOf(value);
        return StringUtils.hasText(asString) ? asString : null;
    }

    private Integer parseLimitEvents(String value) {
        if (!StringUtils.hasText(value)) {
            return 50;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    private AiTriageResponse summarizeWithoutAction(PlatformTriageSkillContext context) {
        return new AiTriageResponse(
                "chat",
                "I understood your message, but I do not have a dedicated tool action for it yet.",
                List.of(
                        "Try: check namespace cart with selector app=cart-app",
                        "Try: show my pods",
                        "Try: show last 10 lines of logs",
                        "Try: summarize",
                        "Try: what is the primary issue",
                        "Try: what are the risks"
                ),
                List.of("Load a summary first."),
                List.of(
                        "Need me to inspect a specific finding code?",
                        "Need pod logs for a specific pod?"
                ),
                "chat",
                false,
                null
        );
    }

    private AiIntent heuristicIntent(String question, DeploymentSummaryResponse summary) {
        if (StringUtils.isEmpty(question)) {
            return AiIntent.chatFallback("empty");
        }

        String normalized = normalize(question);
        boolean hasScopeHints = StringUtils.hasText(extractNamespace(question))
                || StringUtils.hasText(extractSelector(question))
                || StringUtils.hasText(extractRelease(question));

        if (isPodLogIntent(normalized)) {
            return AiIntent.from(PlatformTriageTools.GET_POD_LOGS, 0.96d, collectParamsFromQuestion(question, summary));
        }

        if (isPodListIntent(normalized)) {
            return AiIntent.from(PlatformTriageTools.LIST_PODS, 0.95d, collectParamsFromQuestion(question, summary));
        }

        // Keep health checks stable against the currently loaded summary unless user asks for refresh.
        if (summary != null && isHealthStatusIntent(normalized) && !isExplicitRefreshIntent(normalized)) {
            return AiIntent.from(PlatformTriageTools.SUMMARIZE, 0.94d, collectParamsFromQuestion(question, summary));
        }

        if (isLoadSummaryIntent(normalized) || hasScopeHints) {
            return AiIntent.from(PlatformTriageTools.LOAD_SUMMARY, 0.95d,
                    collectParamsFromQuestion(question, summary));
        }

        if (containsAny(normalized, "summarize", "brief", "overall", "summary now", "what does this mean")) {
            return AiIntent.from(PlatformTriageTools.SUMMARIZE, 0.93d, Map.of());
        }

        if (containsAny(normalized, "primary issue", "primary failure", "top issue", "first fix", "fix first", "main problem")) {
            return AiIntent.from(PlatformTriageTools.PRIMARY_FAILURE, 0.95d, collectParamsFromQuestion(question, summary));
        }

        if (containsAny(normalized, "risk", "warning", "what could go wrong", "what are the risks", "top warning")) {
            return AiIntent.from(PlatformTriageTools.TOP_WARNING, 0.9d, collectParamsFromQuestion(question, summary));
        }

        String code = extractFindingCode(question);
        if (StringUtils.hasText(code)) {
            return AiIntent.from(PlatformTriageTools.FINDING_DETAILS, 0.9d, Map.of("findingCode", code));
        }

        return AiIntent.chatFallback("chat");
    }

    private Map<String, String> collectParamsFromQuestion(String question, DeploymentSummaryResponse summary) {
        Map<String, String> params = new HashMap<>();
        String namespace = extractNamespace(question);
        String selector = extractSelector(question);
        String release = extractRelease(question);
        String limitEvents = pickLimitFromText(question);
        String logLines = pickLogLines(question);
        String podName = pickPodNameFromQuestion(question, summary);

        if (StringUtils.hasText(namespace)) {
            params.put("namespace", namespace);
        }
        if (StringUtils.hasText(selector)) {
            params.put("selector", selector);
        }
        if (StringUtils.hasText(release)) {
            params.put("release", release);
        }
        if (StringUtils.hasText(limitEvents)) {
            params.put("limitEvents", limitEvents);
        }
        if (StringUtils.hasText(logLines)) {
            params.put("logLines", logLines);
        }
        if (StringUtils.hasText(podName)) {
            params.put("podName", podName);
        }

        if (summary != null && summary.target() != null) {
            if (!params.containsKey("namespace") && StringUtils.hasText(summary.target().namespace())) {
                params.put("namespace", summary.target().namespace());
            }
            if (!params.containsKey("selector") && StringUtils.hasText(summary.target().selector())) {
                params.put("selector", summary.target().selector());
            }
            if (!params.containsKey("release") && StringUtils.hasText(summary.target().release())) {
                params.put("release", summary.target().release());
            }
        }

        return params;
    }

    private String buildContextHint(Map<String, Object> context, DeploymentSummaryResponse summary) {
        if (summary != null && summary.target() != null) {
            StringBuilder sb = new StringBuilder("namespace=");
            sb.append(summary.target().namespace());
            if (StringUtils.hasText(summary.target().selector())) {
                sb.append(", selector=").append(summary.target().selector());
            }
            if (StringUtils.hasText(summary.target().release())) {
                sb.append(", release=").append(summary.target().release());
            }
            sb.append(", overall=").append(summary.health().overall());
            sb.append(", findings=").append(summary.findings() == null ? 0 : summary.findings().size());
            return sb.toString();
        }

        if (context == null || context.isEmpty()) {
            return "<none>";
        }

        Object ns = context.get("namespace");
        if (ns != null) {
            return "namespace=" + ns;
        }

        return "<none>";
    }

    private DeploymentSummaryResponse resolveSummary(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        try {
            Object target = context.get("summary");
            if (target != null && target instanceof DeploymentSummaryResponse summary) {
                return summary;
            }
            return objectMapper.convertValue(context, DeploymentSummaryResponse.class);
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse AI context into deployment summary: {}", e.getMessage());
            return null;
        }
    }

    private String extractNamespace(String text) {
        return extractWithPattern(NAMESPACE_PATTERN, text);
    }

    private String extractSelector(String text) {
        return extractWithPattern(SELECTOR_PATTERN, text);
    }

    private String extractRelease(String text) {
        return extractWithPattern(RELEASE_PATTERN, text);
    }

    private String pickLimitFromText(String text) {
        return extractWithPattern(LIMIT_EVENTS_PATTERN, text);
    }

    private String pickLogLines(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        Matcher matcher = LOG_LINES_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        String raw = matcher.group(1);
        if (raw == null) {
            return null;
        }
        if (raw.matches("\\d+")) {
            return raw;
        }
        return switch (raw) {
            case "one" -> "1";
            case "two" -> "2";
            case "three" -> "3";
            case "four" -> "4";
            case "five" -> "5";
            case "six" -> "6";
            case "seven" -> "7";
            case "eight" -> "8";
            case "nine" -> "9";
            case "ten" -> "10";
            case "eleven" -> "11";
            case "twelve" -> "12";
            default -> null;
        };
    }

    private String pickPodNameFromQuestion(String question, DeploymentSummaryResponse summary) {
        if (!StringUtils.hasText(question) || summary == null || summary.objects() == null
                || summary.objects().pods() == null || summary.objects().pods().isEmpty()) {
            return null;
        }

        String normalized = question.toLowerCase(Locale.ROOT);
        for (var pod : summary.objects().pods()) {
            String podName = pod.name();
            if (StringUtils.hasText(podName) && normalized.contains(podName.toLowerCase(Locale.ROOT))) {
                return podName;
            }
        }
        return null;
    }

    private String extractFindingCode(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = FINDING_CODE_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim().toUpperCase();
            if (isKnownFailureCode(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isKnownFailureCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        try {
            FailureCode.valueOf(code);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String extractWithPattern(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    private boolean isPodListIntent(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return false;
        }

        if (normalized.matches(".*\\b(log|logs|tail|tailing|tailing\\s+logs)\\b.*")) {
            return false;
        }

        boolean mentionsPods = normalized.matches(".*\\bpods?\\b.*");
        if (!mentionsPods) {
            return false;
        }

        if (containsAny(normalized, "why", "root cause", "fix", "warning", "risk", "primary")) {
            return false;
        }

        boolean explicitPhrase = containsAny(
                normalized,
                "show my pods", "show me my pods", "show pods", "list pods", "list all pods",
                "what are my pods", "what are the pods", "which pods", "pod status",
                "pods status", "all pods", "show pod", "display pods"
        );
        if (explicitPhrase) {
            return true;
        }

        boolean hasListVerb = normalized.matches(".*\\b(show|list|display|which|what|all)\\b.*");
        return hasListVerb;
    }

    private boolean isHealthStatusIntent(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(
                normalized,
                "health", "health check", "check health", "status", "health status",
                "pod health", "pods health", "is it healthy", "overall health"
        );
    }

    private boolean isLoadSummaryIntent(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(
                normalized,
                "load summary", "deployment summary", "check namespace", "diagnose namespace",
                "diagnose deployment", "scan namespace", "run summary", "load deployment",
                "refresh summary", "reload summary", "recheck namespace"
        );
    }

    private boolean isPodLogIntent(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return false;
        }

        if (isLoadSummaryIntent(normalized)) {
            return false;
        }

        if (!containsAny(normalized, "log", "logs", "tail")) {
            return false;
        }

        if (containsAny(normalized, "latest", "last", "recent", "show", "show me", "view", "display", "fetch", "get", "need", "tail")) {
            return true;
        }

        return containsAny(
                normalized,
                "pod logs",
                "pods logs",
                "show logs",
                "show my logs",
                "show log",
                "show pod logs",
                "show me logs",
                "log output",
                "log lines",
                "tail logs",
                "logs for"
        );
    }

    private boolean isExplicitRefreshIntent(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(normalized, "refresh", "reload", "re run", "rerun", "again", "latest");
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
