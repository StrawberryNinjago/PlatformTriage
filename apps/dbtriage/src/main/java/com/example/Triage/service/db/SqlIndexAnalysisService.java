package com.example.Triage.service.db;

import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbIndex;
import com.example.Triage.model.dto.IndexMatchResult;
import com.example.Triage.util.IndexMatchers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SqlIndexAnalysisService {

    private final DbIndexService indexService;

    public IndexMatchResult analyzeIndexCoverage(
            DbConnectContextDto ctx, 
            String schema, 
            String tableName,
            List<String> whereColumns) throws SQLException {
        
        if (whereColumns == null || whereColumns.isEmpty()) {
            return IndexMatchResult.builder()
                    .tableName(tableName)
                    .queryColumns(List.of())
                    .hasCompositeIndex(false)
                    .hasPartialCoverage(false)
                    .matchedIndexes(List.of())
                    .suggestedIndexes(List.of())
                    .build();
        }

        // Get all indexes for the table
        var indexResponse = indexService.listIndexes(ctx, schema, tableName);
        List<DbIndex> indexes = indexResponse.indexes();

        // Normalize column names
        List<String> normalizedQueryCols = whereColumns.stream()
                .map(String::toLowerCase)
                .toList();

        // Check for composite index covering all WHERE columns
        boolean hasCompositeIndex = indexes.stream()
                .anyMatch(idx -> IndexMatchers.containsAllIgnoreOrder(
                        idx.columns().stream().map(String::toLowerCase).toList(),
                        normalizedQueryCols));

        // Check for partial coverage (prefix match)
        List<String> matchedIndexes = new ArrayList<>();
        boolean hasPartialCoverage = false;

        for (DbIndex index : indexes) {
            List<String> indexCols = index.columns().stream().map(String::toLowerCase).toList();
            
            // Exact match
            if (IndexMatchers.sameSet(indexCols, normalizedQueryCols)) {
                matchedIndexes.add(index.name() + " (exact match)");
            }
            // Full coverage
            else if (IndexMatchers.containsAllIgnoreOrder(indexCols, normalizedQueryCols)) {
                matchedIndexes.add(index.name() + " (covers all columns)");
            }
            // Prefix match
            else if (IndexMatchers.startsWithPrefix(indexCols, normalizedQueryCols)) {
                matchedIndexes.add(index.name() + " (prefix match)");
                hasPartialCoverage = true;
            }
        }

        // Generate suggestions
        List<String> suggestions = new ArrayList<>();
        if (!hasCompositeIndex && whereColumns.size() > 1) {
            suggestions.add("CREATE INDEX idx_" + tableName + "_composite ON " + 
                    tableName + " (" + String.join(", ", whereColumns) + ");");
        }

        return IndexMatchResult.builder()
                .tableName(tableName)
                .queryColumns(whereColumns)
                .hasCompositeIndex(hasCompositeIndex)
                .hasPartialCoverage(hasPartialCoverage)
                .matchedIndexes(matchedIndexes)
                .suggestedIndexes(suggestions)
                .build();
    }
}

