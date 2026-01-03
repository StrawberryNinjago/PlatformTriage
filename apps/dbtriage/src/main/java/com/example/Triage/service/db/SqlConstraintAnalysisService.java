package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.ConstraintViolationRisk;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SqlConstraintAnalysisService {

    private final DbDataSourceFactory dataSourceFactory;
    private final DbIntrospectService introspectService;

    public ConstraintViolationRisk analyzeInsertRisks(
            DbConnectContextDto ctx,
            String schema,
            String tableName,
            List<String> providedColumns) throws SQLException {

        // Get table metadata
        var constraints = introspectService.listConstraints(ctx, schema, tableName);
        var notNullColumns = getNotNullColumns(ctx, schema, tableName);

        // Find missing NOT NULL columns
        Set<String> provided = new HashSet<>(providedColumns);
        List<String> missingNotNull = notNullColumns.stream()
                .filter(col -> !provided.contains(col))
                .toList();

        // Find UNIQUE constraint columns
        List<String> uniqueConstraints = constraints.constraints().stream()
                .filter(c -> "UNIQUE".equals(c.type()) || "PRIMARY KEY".equals(c.type()))
                .flatMap(c -> c.columns().stream())
                .distinct()
                .toList();

        // Find foreign key references
        List<String> fkViolations = constraints.constraints().stream()
                .filter(c -> "FOREIGN KEY".equals(c.type()))
                .flatMap(c -> c.columns().stream())
                .filter(provided::contains)
                .distinct()
                .toList();

        boolean hasRisks = !missingNotNull.isEmpty() || !uniqueConstraints.isEmpty() || !fkViolations.isEmpty();

        return ConstraintViolationRisk.builder()
                .tableName(tableName)
                .missingNotNullColumns(missingNotNull)
                .uniqueConstraintColumns(uniqueConstraints)
                .foreignKeyViolations(fkViolations)
                .hasRisks(hasRisks)
                .build();
    }

    public ConstraintViolationRisk analyzeUpdateRisks(
            DbConnectContextDto ctx,
            String schema,
            String tableName,
            List<String> updateColumns) throws SQLException {

        var constraints = introspectService.listConstraints(ctx, schema, tableName);

        // Check if updating unique columns
        Set<String> uniqueCols = new HashSet<>();
        constraints.constraints().stream()
                .filter(c -> "UNIQUE".equals(c.type()) || "PRIMARY KEY".equals(c.type()))
                .forEach(c -> uniqueCols.addAll(c.columns()));

        List<String> uniqueBeingUpdated = updateColumns.stream()
                .filter(uniqueCols::contains)
                .toList();

        // Check if updating FK columns
        Set<String> fkCols = new HashSet<>();
        constraints.constraints().stream()
                .filter(c -> "FOREIGN KEY".equals(c.type()))
                .forEach(c -> fkCols.addAll(c.columns()));

        List<String> fkBeingUpdated = updateColumns.stream()
                .filter(fkCols::contains)
                .toList();

        boolean hasRisks = !uniqueBeingUpdated.isEmpty() || !fkBeingUpdated.isEmpty();

        return ConstraintViolationRisk.builder()
                .tableName(tableName)
                .missingNotNullColumns(List.of())
                .uniqueConstraintColumns(uniqueBeingUpdated)
                .foreignKeyViolations(fkBeingUpdated)
                .hasRisks(hasRisks)
                .build();
    }

    private List<String> getNotNullColumns(DbConnectContextDto ctx, String schema, String tableName) 
            throws SQLException {
        
        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND is_nullable = 'NO'
                  AND column_default IS NULL
                """;

        List<String> notNullCols = new ArrayList<>();
        var ds = dataSourceFactory.build(ctx);
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, schema);
            ps.setString(2, tableName);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notNullCols.add(rs.getString("column_name"));
                }
            }
        }
        
        return notNullCols;
    }
}

