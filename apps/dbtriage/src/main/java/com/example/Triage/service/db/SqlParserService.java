package com.example.Triage.service.db;

import com.example.Triage.model.enums.SqlOperationType;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlParserService {

    @Data
    public static class ParsedSql {
        private SqlOperationType operationType;
        private String tableName;
        private List<String> columns = new ArrayList<>();
        private List<String> whereColumns = new ArrayList<>();
        private Map<String, String> insertValues = new HashMap<>();
        private Map<String, String> updateValues = new HashMap<>();
        private boolean isValid;
        private String errorMessage;
    }

    public ParsedSql parseSql(String sql) {
        ParsedSql result = new ParsedSql();
        
        if (sql == null || sql.trim().isEmpty()) {
            result.setValid(false);
            result.setErrorMessage("SQL is empty");
            return result;
        }

        // Normalize SQL
        String normalized = sql.trim().replaceAll("\\s+", " ");
        
        // Check for multiple statements (semicolons not at end)
        if (normalized.replaceAll(";\\s*$", "").contains(";")) {
            result.setValid(false);
            result.setErrorMessage("Multiple statements detected. Only single statements are allowed.");
            return result;
        }

        // Detect operation type
        SqlOperationType opType = detectOperationType(normalized);
        result.setOperationType(opType);

        try {
            switch (opType) {
                case SELECT -> parseSelect(normalized, result);
                case INSERT -> parseInsert(normalized, result);
                case UPDATE -> parseUpdate(normalized, result);
                case DELETE -> parseDelete(normalized, result);
                default -> {
                    result.setValid(false);
                    result.setErrorMessage("Unsupported SQL operation");
                }
            }
        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage("Parse error: " + e.getMessage());
        }

        return result;
    }

    private SqlOperationType detectOperationType(String sql) {
        String upper = sql.toUpperCase().trim();
        if (upper.startsWith("SELECT")) return SqlOperationType.SELECT;
        if (upper.startsWith("INSERT")) return SqlOperationType.INSERT;
        if (upper.startsWith("UPDATE")) return SqlOperationType.UPDATE;
        if (upper.startsWith("DELETE")) return SqlOperationType.DELETE;
        return SqlOperationType.UNKNOWN;
    }

    private void parseSelect(String sql, ParsedSql result) {
        // Extract table name from FROM clause
        Pattern fromPattern = Pattern.compile("FROM\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(sql);
        if (fromMatcher.find()) {
            result.setTableName(extractTableName(fromMatcher.group(1)));
        }

        // Extract WHERE columns
        parseWhereClause(sql, result);
        
        result.setValid(true);
    }

    private void parseInsert(String sql, ParsedSql result) {
        // Pattern: INSERT INTO table (col1, col2) VALUES (?, ?)
        Pattern insertPattern = Pattern.compile(
            "INSERT\\s+INTO\\s+([\\w.]+)\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = insertPattern.matcher(sql);
        
        if (matcher.find()) {
            result.setTableName(extractTableName(matcher.group(1)));
            String columnList = matcher.group(2);
            result.setColumns(parseColumnList(columnList));
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setErrorMessage("Could not parse INSERT statement");
        }
    }

    private void parseUpdate(String sql, ParsedSql result) {
        // Pattern: UPDATE table SET col1 = ?, col2 = ? WHERE ...
        Pattern updatePattern = Pattern.compile(
            "UPDATE\\s+([\\w.]+)\\s+SET",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = updatePattern.matcher(sql);
        
        if (matcher.find()) {
            result.setTableName(extractTableName(matcher.group(1)));
            
            // Extract SET columns
            Pattern setPattern = Pattern.compile("SET\\s+(.+?)(?:WHERE|$)", Pattern.CASE_INSENSITIVE);
            Matcher setMatcher = setPattern.matcher(sql);
            if (setMatcher.find()) {
                String setClause = setMatcher.group(1);
                result.setColumns(extractUpdateColumns(setClause));
            }
            
            // Extract WHERE columns
            parseWhereClause(sql, result);
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setErrorMessage("Could not parse UPDATE statement");
        }
    }

    private void parseDelete(String sql, ParsedSql result) {
        // Pattern: DELETE FROM table WHERE ...
        Pattern deletePattern = Pattern.compile(
            "DELETE\\s+FROM\\s+([\\w.]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = deletePattern.matcher(sql);
        
        if (matcher.find()) {
            result.setTableName(extractTableName(matcher.group(1)));
            parseWhereClause(sql, result);
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setErrorMessage("Could not parse DELETE statement");
        }
    }

    private void parseWhereClause(String sql, ParsedSql result) {
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+?)(?:ORDER BY|GROUP BY|LIMIT|;|$)", 
            Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = wherePattern.matcher(sql);
        
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            result.setWhereColumns(extractWhereColumns(whereClause));
        }
    }

    private String extractTableName(String tableRef) {
        // Handle schema.table format
        String[] parts = tableRef.split("\\.");
        return parts.length > 1 ? parts[1] : parts[0];
    }

    private List<String> parseColumnList(String columnList) {
        List<String> columns = new ArrayList<>();
        for (String col : columnList.split(",")) {
            columns.add(col.trim());
        }
        return columns;
    }

    private List<String> extractUpdateColumns(String setClause) {
        List<String> columns = new ArrayList<>();
        // Match column names before = sign
        Pattern colPattern = Pattern.compile("([\\w]+)\\s*=", Pattern.CASE_INSENSITIVE);
        Matcher matcher = colPattern.matcher(setClause);
        while (matcher.find()) {
            columns.add(matcher.group(1).trim());
        }
        return columns;
    }

    private List<String> extractWhereColumns(String whereClause) {
        List<String> columns = new ArrayList<>();
        // Match column names in WHERE conditions (simple extraction)
        Pattern colPattern = Pattern.compile("([\\w]+)\\s*(?:=|<|>|<=|>=|!=|LIKE|IN)", 
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = colPattern.matcher(whereClause);
        while (matcher.find()) {
            String col = matcher.group(1).toUpperCase();
            // Filter out SQL keywords
            if (!isSqlKeyword(col)) {
                columns.add(matcher.group(1).trim());
            }
        }
        return columns;
    }

    private boolean isSqlKeyword(String word) {
        Set<String> keywords = Set.of("AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "IS");
        return keywords.contains(word);
    }
}

