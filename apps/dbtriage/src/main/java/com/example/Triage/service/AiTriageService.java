package com.example.Triage.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.Triage.model.request.AiTriageRequest;
import com.example.Triage.model.response.AiTriageResponse;
import com.example.Triage.service.ai.AiIntent;
import com.example.Triage.service.ai.DbTriageSkill;
import com.example.Triage.service.ai.DbTriageSkillContext;
import com.example.Triage.service.ai.DbTriageSkillMetadata;
import com.example.Triage.service.ai.DbTriageSkillRegistry;
import com.example.Triage.service.ai.DbTriageSkillResult;
import com.example.Triage.service.ai.DbTriageTools;
import com.example.Triage.service.ai.OpenAiIntentRouter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTriageService {

    private static final double OPENAI_MIN_CONFIDENCE = 0.68d;
    private static final Pattern TABLE_WITH_SCHEMA_PATTERN = Pattern.compile("(?i)\\b([A-Za-z_][\\w]*)\\.([A-Za-z_][\\w]*)\\b");
    private static final Pattern TABLE_EXTRACT_PATTERN =
            Pattern.compile("(?i)(?:table|tbl|table\\s+details|table\\s+diagnostics|permission|privilege|ownership|inspect|show\\s+ownership|check\\s+ownership|columns\\s+for|table\\s+details|table\\s+diagnostic)\\s+(?:for|on|of|of\\s+table|for\\s+table)?\\s*[`\"']?([A-Za-z_][\\w]*)[`\"']?");
    private static final Pattern TABLE_NAME_WITH_TRAILING_KEYWORD_PATTERN =
            Pattern.compile("(?i)([A-Za-z_][\\w]*)\\s+(?:table|tbl)\\b");
    private static final Pattern INDEX_QUERY_PATTERN =
            Pattern.compile("(?i)(?:what\\s+is|show|show me|list|display|get|find|what\\s+are)\\s+(?:\\w+\\s+)?(?:index|indexes)\\s+(?:for|of|on)\\s+[`\"']?([A-Za-z_][\\w]*)[`\"']?");
    private static final Pattern SQL_CODE_BLOCK_PATTERN =
            Pattern.compile("(?s)```(?:sql)?\\n(.*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_QUERY_PATTERN =
            Pattern.compile("(?i)(?:search|find|show)\\s+(?:me\\s+)?(?:tables?|table\\s+names?)\\s+(?:that|which|where|with|for|containing|like)\\s+([A-Za-z_][A-Za-z0-9_%]*)");
    private static final Pattern SQL_EXTRACT_PATTERN =
            Pattern.compile("(?is).*?(\\b(?:select|insert|update|delete|with|create|alter|drop)\\b[\\s\\S]*)");

    private final OpenAiIntentRouter openAiIntentRouter;
    private final DbTriageSkillRegistry skillRegistry;

    public AiTriageResponse triage(AiTriageRequest request) {
        String question = trim(request.question());
        String connectionId = trim(request.connectionId());
        String schema = resolveSchema(request.context());

        AiIntent intent = resolveIntent(question, schema);
        if (isToolRequiringConnection(intent.tool()) && !StringUtils.hasText(connectionId)) {
            return disconnectedResponse(intent.tool(), schema);
        }

        return executeTool(intent, connectionId, schema, question);
    }

    private AiTriageResponse executeTool(AiIntent intent, String connectionId, String schema, String question) {
        Optional<DbTriageSkill> optionalSkill = skillRegistry.resolve(intent.tool());
        if (optionalSkill.isEmpty()) {
            return summarizeWithoutAction(connectionId, schema, question);
        }

        DbTriageSkill skill = optionalSkill.get();
        DbTriageSkillContext context = buildSkillContext(intent, connectionId, schema, question);
        try {
            DbTriageSkillResult result = skill.execute(context);
            return result == null
                    ? summarizeWithoutAction(connectionId, schema, question)
                    : result.toResponse();
        } catch (Exception e) {
            log.error("DB triage assistant tool execution failed", e);
            return new AiTriageResponse(
                    "tool_error",
                    "I recognized the request but could not execute the DB action: " + e.getMessage(),
                    List.of("Tool execution failed for: " + intent.tool()),
                    List.of("Retry with a clearer request (table name/SQL)."),
                    List.of("Check connection and privileges."),
                    intent.tool(),
                    false,
                    null);
        }
    }

    private DbTriageSkillContext buildSkillContext(AiIntent intent, String connectionId, String schema, String question) {
        String normalizedSchema = resolveSchemaLabel(schema);
        Map<String, String> params = resolveToolParameters(intent, question, normalizedSchema);
        return new DbTriageSkillContext(question, connectionId, normalizedSchema, intent, params);
    }

    private AiTriageResponse disconnectedResponse(String requestedTool, String schema) {
        return new AiTriageResponse(
                "disconnected",
                "I can run this as a DB tool, but I do not have an active connection.",
                List.of("No action executed yet.", "Current schema: " + resolveSchemaLabel(schema)),
                List.of(
                        "Connect to the database first.",
                        "Then I can run Flyway health, list tables, or analyze SQL immediately."),
                List.of(
                        "Would you like me to pre-fill connection defaults for localhost?",
                        "Need one of these prompts first: check flyway health, list tables, show table details."),
                requestedTool,
                false,
                null);
    }

    private AiTriageResponse summarizeWithoutAction(String connectionId, String schema, String question) {
        if (!StringUtils.hasText(connectionId)) {
            return disconnectedResponse("chat", schema);
        }

        return new AiTriageResponse(
                "chat",
                "I understood your message, but I do not have a dedicated tool action for it yet.",
                List.of(
                        "Try: check flyway health",
                        "Try: what are the tables",
                        "Try: list public tables",
                        "Try: show table details for cart_item",
                        "Try: check index and constraints for cart_item",
                        "Try: compare environments",
                        "Try: do I have permission for cart_item",
                        "Try: what is the database version",
                        "Try: why this SQL does not work: ..."),
                List.of("Run one of the tool prompts for an actionable response."),
                List.of(
                        "Would you like me to explain all available DB tools?"),
                "chat",
                false,
                null);
    }

    private AiIntent resolveIntent(String question, String schema) {
        AiIntent heuristic = heuristicIntent(question, schema);
        if (!"chat".equals(heuristic.tool())) {
            return heuristic;
        }

        Optional<AiIntent> llmIntent = openAiIntentRouter.route(question, schema);
        if (llmIntent.isPresent()) {
            AiIntent parsed = llmIntent.get();
            if (!"chat".equals(parsed.tool()) && isSupportedTool(parsed.tool()) && parsed.confidence() >= OPENAI_MIN_CONFIDENCE) {
                Map<String, String> params = withSchema(parsed.parameters(), schema);
                return AiIntent.from(parsed.tool(), parsed.confidence(), params);
            }
        }

        return heuristic;
    }

    private AiIntent heuristicIntent(String question, String schema) {
        String normalized = normalizedIntentText(question);

        if (containsAny(normalized,
                "who am i", "who is", "current user", "identity", "connection info", "verify connection", "who is connected")) {
            return AiIntent.from(DbTriageTools.VERIFY_CONNECTION, 0.95d, Map.of("schema", schema));
        }
        if (containsAny(
                normalized,
                "current db version",
                "current database version",
                "database version",
                "db version",
                "version of postgres",
                "postgres version",
                "postgresql version",
                "server version",
                "what is the version",
                "what is current version",
                "show server_version")) {
            return AiIntent.from(DbTriageTools.VERIFY_CONNECTION, 0.95d, Map.of("schema", schema));
        }
        if (containsAny(
                normalized,
                "compare environments",
                "compare environment",
                "compare env",
                "compare envs",
                "environment alignment",
                "environment align",
                "do my two environments align",
                "do my two environment align",
                "do two environments align",
                "do two environment align",
                "are my two environments aligned",
                "are my two environment aligned",
                "are environments aligned",
                "are environment aligned",
                "envs align",
                "do envs align")) {
            return AiIntent.from(DbTriageTools.COMPARE_ENVIRONMENTS, 0.95d, Map.of("schema", schema));
        }
        if (containsAny(normalized, "flyway", "migration", "health check", "migration status", "migration health")) {
            return AiIntent.from(DbTriageTools.FLYWAY_HEALTH, 0.95d, Map.of("schema", schema));
        }
        if (containsAny(normalized, "show the tables", "list tables", "show all tables", "what tables", "all tables", "tables in")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.95d, Map.of("schema", schema));
        }
        if (containsAny(normalized, "list public tables", "show public tables", "all public tables", "tables in public")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.95d, Map.of("schema", schema));
        }
        if (normalized.matches(".*\\bpublic\\b.*\\btables\\b.*")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.93d, Map.of("schema", schema));
        }
        if (normalized.matches(".*\\bshow\\b.*\\btables\\b.*") || normalized.matches(".*\\blist\\b.*\\btables\\b.*")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.94d, Map.of("schema", schema));
        }
        if (normalized.matches(".*\\blist\\b.*\\bpublic\\b.*\\btables\\b.*")
                || normalized.matches(".*\\bshow\\b.*\\bpublic\\b.*\\btables\\b.*")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.94d, Map.of("schema", schema));
        }
        if (containsAny(normalized, "which tables", "what are the tables", "what are tables", "how many tables")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.92d, Map.of("schema", schema));
        }
        if (normalized.matches(".*\\bshow\\b.*\\ball\\b.*\\btables\\b.*")
                || normalized.matches(".*\\blist\\b.*\\ball\\b.*\\btables\\b.*")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.91d, Map.of("schema", schema));
        }
        if (containsAny(normalized, "what are my tables", "my tables", "my table list", "table list")) {
            return AiIntent.from(DbTriageTools.LIST_TABLES, 0.91d, Map.of("schema", schema));
        }

        String table = extractTableName(question);
        if (containsAny(normalized, "permission", "privilege", "ownership", "grant", "grants")
                ) {
            return AiIntent.from(
                    DbTriageTools.CHECK_PRIVILEGES,
                    0.91d,
                    StringUtils.hasText(table)
                            ? withSchema(Map.of("tableName", table), schema)
                            : Map.of("schema", resolveSchemaLabel(schema)));
        }

        if (containsAny(
                normalized,
                "inspect",
                "show table details",
                "table details",
                "table detail",
                "inspect table",
                "columns for",
                "detail",
                "details")
                ) {
            return AiIntent.from(
                    DbTriageTools.TABLE_DETAILS,
                    0.9d,
                    StringUtils.hasText(table)
                            ? withSchema(Map.of("tableName", table), schema)
                            : Map.of("schema", resolveSchemaLabel(schema)));
        }

        if (containsAny(
                normalized,
                "check index",
                "check indexes",
                "check constraint",
                "check constraints",
                "index",
                "indexes",
                "constraint",
                "constraints",
                "index and constraints")
                && StringUtils.hasText(table)) {
            return AiIntent.from(
                    DbTriageTools.TABLE_DETAILS,
                    0.9d,
                    withSchema(Map.of("tableName", table), schema));
        }

        String indexTable = extractIndexTableName(question);
        if (StringUtils.hasText(indexTable) && containsAny(normalized, "index", "indexes", "indexing")) {
            return AiIntent.from(
                    DbTriageTools.TABLE_DETAILS,
                    0.9d,
                    withSchema(Map.of("tableName", indexTable), schema));
        }

        if (containsAny(normalized, "do i have", "can i", "do we have") && StringUtils.hasText(table)) {
            return AiIntent.from(DbTriageTools.CHECK_PRIVILEGES, 0.9d, withSchema(Map.of("tableName", table), schema));
        }

        String sql = extractSql(question);
        if (containsAny(normalized, "why this sql", "sql does not work", "why is this sql", "analy")
                || (containsAny(normalized, "select", "insert", "update", "delete") && StringUtils.hasText(sql))) {
            return AiIntent.from(DbTriageTools.SQL_ANALYSIS, 0.88d,
                    withSchema(sql == null ? Map.of() : Map.of("sql", sql), schema));
        }

        String search = extractSearchQuery(question);
        if ((containsAny(normalized, "find table", "search table", "search tables") && StringUtils.hasText(search))
                || containsAny(normalized, "table like", "table contains")) {
            return AiIntent.from(DbTriageTools.SEARCH_TABLES, 0.87d,
                    withSearchParam(Map.of(), search));
        }

        return AiIntent.chatFallback("chat");
    }

    private boolean isToolRequiringConnection(String tool) {
        return skillRegistry.resolve(tool)
                .map(DbTriageSkill::metadata)
                .map(DbTriageSkillMetadata::requiresConnection)
                .orElse(false);
    }

    private boolean isSupportedTool(String tool) {
        return skillRegistry.isSupported(tool);
    }

    private String resolveSchema(Map<String, Object> context) {
        if (context != null && context.get("schema") != null) {
            String schema = String.valueOf(context.get("schema"));
            if (StringUtils.hasText(schema)) {
                return schema;
            }
        }
        return "public";
    }

    private Map<String, String> resolveToolParameters(AiIntent intent, String question, String schema) {
        Map<String, String> params = new HashMap<>();
        if (intent.parameters() != null) {
            params.putAll(intent.parameters());
        }

        params = withSchema(params, schema);

        return switch (intent.tool()) {
            case DbTriageTools.SEARCH_TABLES -> {
                String resolvedQuery = resolveParam(intent, question, "searchQuery");
                if (StringUtils.hasText(resolvedQuery)) {
                    params.put("searchQuery", resolvedQuery);
                }
                yield params;
            }
            case DbTriageTools.TABLE_DETAILS, DbTriageTools.CHECK_PRIVILEGES -> {
                String resolvedTable = resolveParam(intent, question, "tableName");
                if (StringUtils.hasText(resolvedTable)) {
                    ParsedTable parsedTable = parseTable(resolvedTable, schema);
                    params.put("schema", parsedTable.schema());
                    params.put("tableName", parsedTable.name());
                }
                yield params;
            }
            case DbTriageTools.SQL_ANALYSIS -> {
                String resolvedSql = resolveParam(intent, question, "sql");
                if (StringUtils.hasText(resolvedSql)) {
                    params.put("sql", resolvedSql);
                }
                String operationHint = resolveParam(intent, question, "operationType");
                if (StringUtils.hasText(operationHint)) {
                    params.put("operationType", operationHint);
                }
                yield params;
            }
            default -> params;
        };
    }

    private String resolveParam(AiIntent intent, String question, String key) {
        String provided = intent.parameters() != null ? intent.parameters().get(key) : null;
        if (StringUtils.hasText(provided)) {
            return provided;
        }

        return switch (key) {
            case "tableName" -> extractTableName(question);
            case "searchQuery" -> extractSearchQuery(question);
            case "sql" -> extractSql(question);
            default -> null;
        };
    }

    private ParsedTable parseTable(String rawTable, String defaultSchema) {
        Matcher matcher = TABLE_WITH_SCHEMA_PATTERN.matcher(rawTable);
        if (matcher.find()) {
            return new ParsedTable(matcher.group(1), matcher.group(2));
        }

        String value = trim(rawTable);
        if (value.contains(".")) {
            String[] parts = value.split("\\.");
            if (parts.length == 2) {
                return new ParsedTable(trim(parts[0]), trim(parts[1]));
            }
        }

        return new ParsedTable(resolveSchemaLabel(defaultSchema), value);
    }

    private String extractTableName(String text) {
        String tableName = extractWithPattern(TABLE_EXTRACT_PATTERN, text);
        if (StringUtils.hasText(tableName)) {
            return tableName;
        }

        return extractWithPattern(TABLE_NAME_WITH_TRAILING_KEYWORD_PATTERN, text);
    }

    private String extractSearchQuery(String text) {
        return extractWithPattern(SEARCH_QUERY_PATTERN, text);
    }

    private String extractIndexTableName(String text) {
        return extractWithPattern(INDEX_QUERY_PATTERN, text);
    }

    private String extractSql(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher blockMatcher = SQL_CODE_BLOCK_PATTERN.matcher(text);
        if (blockMatcher.find()) {
            return blockMatcher.group(1).trim();
        }

        String normalized = text.trim();
        int colon = normalized.indexOf(":");
        if (colon >= 0) {
            String tail = normalized.substring(colon + 1).trim();
            if (looksLikeSql(tail)) {
                return tail;
            }
        }

        Matcher matcher = SQL_EXTRACT_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }

        if (looksLikeSql(normalized)) {
            return normalized;
        }

        return null;
    }

    private String extractWithPattern(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return trim(matcher.group(1));
        }

        return null;
    }

    private Map<String, String> withSearchParam(Map<String, String> base, String searchQuery) {
        Map<String, String> result = new HashMap<>(base == null ? Map.of() : base);
        if (StringUtils.hasText(searchQuery)) {
            result.put("searchQuery", searchQuery);
        }
        return result;
    }

    private Map<String, String> withSchema(Map<String, String> params, String schema) {
        Map<String, String> result = new HashMap<>();
        result.put("schema", resolveSchemaLabel(schema));
        if (params != null) {
            result.putAll(params);
        }
        return result;
    }

    private String resolveSchemaLabel(String schema) {
        return StringUtils.hasText(schema) ? schema : "public";
    }

    private String trim(String input) {
        return input == null ? "" : input.trim();
    }

    private String normalize(String input) {
        return input == null ? "" : input.replaceAll("\\s+", " ").trim();
    }

    private String normalizedIntentText(String input) {
        return normalize(input)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeSql(String text) {
        String lower = normalize(text).toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "select ", "insert ", "update ", "delete ", "with ", "create ", "alter ", "drop ");
    }

    private record ParsedTable(String schema, String name) {
    }
}
