package com.example.Triage.service.db;

import com.example.Triage.model.dto.CascadeAnalysisResult;
import com.example.Triage.model.dto.DbConnectContextDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SqlCascadeAnalysisService {

    private final DbDataSourceFactory dataSourceFactory;

    public CascadeAnalysisResult analyzeCascadeImpact(
            DbConnectContextDto ctx,
            String schema,
            String tableName) throws SQLException {

        // Get all foreign keys that reference this table (cascade delete triggers)
        List<String> affectedTables = getCascadingTables(ctx, schema, tableName);
        
        // Check for recursive cascades (self-referential)
        boolean hasRecursive = checkRecursiveCascade(ctx, schema, tableName);
        
        // Calculate cascade depth
        String cascadeDepth = calculateCascadeDepth(affectedTables.size(), hasRecursive);

        return CascadeAnalysisResult.builder()
                .tableName(tableName)
                .cascadingForeignKeys(affectedTables.size())
                .affectedTables(affectedTables)
                .hasRecursiveCascade(hasRecursive)
                .cascadeDepth(cascadeDepth)
                .build();
    }

    private List<String> getCascadingTables(DbConnectContextDto ctx, String schema, String tableName) 
            throws SQLException {
        
        String sql = """
                SELECT DISTINCT
                    tc.table_schema || '.' || tc.table_name AS dependent_table,
                    rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc 
                    ON tc.constraint_name = rc.constraint_name
                JOIN information_schema.key_column_usage kcu 
                    ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu 
                    ON ccu.constraint_name = tc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND ccu.table_schema = ?
                  AND ccu.table_name = ?
                  AND (rc.delete_rule = 'CASCADE' OR rc.delete_rule = 'SET NULL' OR rc.delete_rule = 'SET DEFAULT')
                """;

        List<String> tables = new ArrayList<>();
        var ds = dataSourceFactory.build(ctx);
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, schema);
            ps.setString(2, tableName);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String depTable = rs.getString("dependent_table");
                    String deleteRule = rs.getString("delete_rule");
                    tables.add(depTable + " (" + deleteRule + ")");
                }
            }
        }
        
        return tables;
    }

    private boolean checkRecursiveCascade(DbConnectContextDto ctx, String schema, String tableName) 
            throws SQLException {
        
        String sql = """
                SELECT COUNT(*) as recursive_count
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu 
                    ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu 
                    ON ccu.constraint_name = tc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = ?
                  AND tc.table_name = ?
                  AND ccu.table_schema = ?
                  AND ccu.table_name = ?
                """;

        var ds = dataSourceFactory.build(ctx);
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, schema);
            ps.setString(2, tableName);
            ps.setString(3, schema);
            ps.setString(4, tableName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("recursive_count") > 0;
                }
            }
        }
        
        return false;
    }

    private String calculateCascadeDepth(int cascadeCount, boolean hasRecursive) {
        if (hasRecursive) {
            return "HIGH (recursive detected)";
        } else if (cascadeCount >= 5) {
            return "HIGH (" + cascadeCount + " tables)";
        } else if (cascadeCount >= 2) {
            return "MEDIUM (" + cascadeCount + " tables)";
        } else if (cascadeCount == 1) {
            return "LOW (1 table)";
        } else {
            return "NONE";
        }
    }
}

