package com.example.Triage.service.db;

import com.example.Triage.model.dto.*;
import com.example.Triage.model.enums.DriftSeverity;
import com.example.Triage.model.enums.DriftStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Service to detect schema drift between two environments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaDriftService {

    private final DbDataSourceFactory dataSourceFactory;

    // Maximum number of MATCH items to include (to prevent huge responses)
    private static final int MAX_MATCH_ITEMS = 100;

    /**
     * Compare tables between source and target
     */
    public DriftSection compareTablesSection(
            DbConnectContextDto sourceCtx,
            DbConnectContextDto targetCtx,
            EnvironmentCapabilityMatrix sourceCapabilities,
            EnvironmentCapabilityMatrix targetCapabilities,
            List<String> specificTables) {

        // Check availability
        if (!sourceCapabilities.tables().available() || !targetCapabilities.tables().available()) {
            String reason = !sourceCapabilities.tables().available() 
                    ? sourceCapabilities.tables().message()
                    : targetCapabilities.tables().message();
            
            return new DriftSection(
                    "Tables",
                    "Table existence and structure",
                    SectionAvailability.createUnavailable(
                            reason,
                            "read access to information_schema.tables",
                            "Cannot detect missing or extra tables"
                    ),
                    Collections.emptyList(),
                    0, 0, 0
            );
        }

        DataSource sourceDs = dataSourceFactory.build(sourceCtx);
        DataSource targetDs = dataSourceFactory.build(targetCtx);

        Set<String> sourceTables = getTables(sourceDs, sourceCtx.schema(), specificTables);
        Set<String> targetTables = getTables(targetDs, targetCtx.schema(), specificTables);

        List<DriftItem> driftItems = new ArrayList<>();
        int matchCount = 0;
        int differCount = 0;

        // Tables in source but not in target
        for (String table : sourceTables) {
            if (!targetTables.contains(table)) {
                driftItems.add(new DriftItem(
                        "Compatibility",
                        table,
                        "exists",
                        true,
                        false,
                        DriftStatus.DIFFER,
                        DriftSeverity.ERROR,
                        "High",
                        String.format("Table '%s' exists in source but missing in target", table)
                ));
                differCount++;
            } else {
                // Only add MATCH items if we haven't exceeded the limit
                if (matchCount < MAX_MATCH_ITEMS) {
                    driftItems.add(new DriftItem(
                            "Compatibility",
                            table,
                            "exists",
                            true,
                            true,
                            DriftStatus.MATCH,
                            DriftSeverity.INFO,
                            null,
                            String.format("Table '%s' exists in both environments", table)
                    ));
                }
                matchCount++;
            }
        }

        // Tables in target but not in source
        for (String table : targetTables) {
            if (!sourceTables.contains(table)) {
                driftItems.add(new DriftItem(
                        "Compatibility",
                        table,
                        "exists",
                        false,
                        true,
                        DriftStatus.DIFFER,
                        DriftSeverity.WARN,
                        "Low",
                        String.format("Table '%s' exists in target but not in source", table)
                ));
                differCount++;
            }
        }

        return new DriftSection(
                "Tables",
                "Table existence and structure",
                SectionAvailability.createAvailable(),
                driftItems,
                matchCount,
                differCount,
                0
        );
    }

    /**
     * Compare columns between source and target
     */
    public DriftSection compareColumnsSection(
            DbConnectContextDto sourceCtx,
            DbConnectContextDto targetCtx,
            EnvironmentCapabilityMatrix sourceCapabilities,
            EnvironmentCapabilityMatrix targetCapabilities,
            Set<String> commonTables) {

        if (!sourceCapabilities.columns().available() || !targetCapabilities.columns().available()) {
            String reason = !sourceCapabilities.columns().available()
                    ? sourceCapabilities.columns().message()
                    : targetCapabilities.columns().message();

            return new DriftSection(
                    "Columns",
                    "Column definitions and types",
                    SectionAvailability.createUnavailable(
                            reason,
                            "read access to information_schema.columns",
                            "Cannot detect column type mismatches or missing columns"
                    ),
                    Collections.emptyList(),
                    0, 0, 0
            );
        }

        DataSource sourceDs = dataSourceFactory.build(sourceCtx);
        DataSource targetDs = dataSourceFactory.build(targetCtx);

        List<DriftItem> driftItems = new ArrayList<>();
        int matchCount = 0;
        int differCount = 0;

        for (String table : commonTables) {
            Map<String, ColumnInfo> sourceColumns = getColumns(sourceDs, sourceCtx.schema(), table);
            Map<String, ColumnInfo> targetColumns = getColumns(targetDs, targetCtx.schema(), table);

            // Compare columns
            Set<String> allColumns = new HashSet<>();
            allColumns.addAll(sourceColumns.keySet());
            allColumns.addAll(targetColumns.keySet());

            for (String column : allColumns) {
                ColumnInfo sourceCol = sourceColumns.get(column);
                ColumnInfo targetCol = targetColumns.get(column);

                String objectName = table + "." + column;

                if (sourceCol == null) {
                    driftItems.add(new DriftItem(
                            "Compatibility",
                            objectName,
                            "exists",
                            false,
                            true,
                            DriftStatus.DIFFER,
                            DriftSeverity.WARN,
                            "Low",
                            String.format("Column '%s' exists in target but not in source", objectName)
                    ));
                    differCount++;
                } else if (targetCol == null) {
                    driftItems.add(new DriftItem(
                            "Compatibility",
                            objectName,
                            "exists",
                            true,
                            false,
                            DriftStatus.DIFFER,
                            DriftSeverity.ERROR,
                            "High",
                            String.format("Column '%s' exists in source but missing in target", objectName)
                    ));
                    differCount++;
                } else {
                    // Both exist - compare attributes
                    if (!sourceCol.dataType.equals(targetCol.dataType)) {
                        driftItems.add(new DriftItem(
                                "Compatibility",
                                objectName,
                                "data_type",
                                sourceCol.dataType,
                                targetCol.dataType,
                                DriftStatus.DIFFER,
                                DriftSeverity.ERROR,
                                "High",
                                String.format("Column '%s' type mismatch: %s vs %s", objectName, sourceCol.dataType, targetCol.dataType)
                        ));
                        differCount++;
                    }

                    if (sourceCol.isNullable != targetCol.isNullable) {
                        driftItems.add(new DriftItem(
                                "Compatibility",
                                objectName,
                                "is_nullable",
                                sourceCol.isNullable,
                                targetCol.isNullable,
                                DriftStatus.DIFFER,
                                DriftSeverity.ERROR,
                                "High",
                                String.format("Column '%s' nullability mismatch: %s vs %s", objectName, sourceCol.isNullable, targetCol.isNullable)
                        ));
                        differCount++;
                    }

                    if (!Objects.equals(sourceCol.columnDefault, targetCol.columnDefault)) {
                        driftItems.add(new DriftItem(
                                "Compatibility",
                                objectName,
                                "default",
                                sourceCol.columnDefault,
                                targetCol.columnDefault,
                                DriftStatus.DIFFER,
                                DriftSeverity.WARN,
                                "Medium",
                                String.format("Column '%s' default mismatch: %s vs %s", objectName, sourceCol.columnDefault, targetCol.columnDefault)
                        ));
                        differCount++;
                    }

                    // Add MATCH item if no differences found (and under limit)
                    if (driftItems.stream().noneMatch(d -> d.objectName().equals(objectName))) {
                        if (matchCount < MAX_MATCH_ITEMS) {
                            driftItems.add(new DriftItem(
                                    "Compatibility",
                                    objectName,
                                    "all_attributes",
                                    "matches",
                                    "matches",
                                    DriftStatus.MATCH,
                                    DriftSeverity.INFO,
                                    null,
                                    String.format("Column '%s' matches in both environments", objectName)
                            ));
                        }
                        matchCount++;
                    }
                }
            }
        }

        return new DriftSection(
                "Columns",
                "Column definitions and types",
                SectionAvailability.createAvailable(),
                driftItems,
                matchCount,
                differCount,
                0
        );
    }

    /**
     * Compare constraints between source and target
     */
    public DriftSection compareConstraintsSection(
            DbConnectContextDto sourceCtx,
            DbConnectContextDto targetCtx,
            EnvironmentCapabilityMatrix sourceCapabilities,
            EnvironmentCapabilityMatrix targetCapabilities,
            Set<String> commonTables) {

        if (!sourceCapabilities.constraints().available() || !targetCapabilities.constraints().available()) {
            String reason = !sourceCapabilities.constraints().available()
                    ? sourceCapabilities.constraints().message()
                    : targetCapabilities.constraints().message();

            return new DriftSection(
                    "Constraints",
                    "Primary keys, foreign keys, unique constraints",
                    SectionAvailability.createUnavailable(
                            reason,
                            "read access to information_schema.table_constraints",
                            "Cannot detect constraint mismatches"
                    ),
                    Collections.emptyList(),
                    0, 0, 0
            );
        }

        DataSource sourceDs = dataSourceFactory.build(sourceCtx);
        DataSource targetDs = dataSourceFactory.build(targetCtx);

        List<DriftItem> driftItems = new ArrayList<>();
        int matchCount = 0;
        int differCount = 0;

        for (String table : commonTables) {
            Map<String, String> sourceConstraints = getConstraints(sourceDs, sourceCtx.schema(), table);
            Map<String, String> targetConstraints = getConstraints(targetDs, targetCtx.schema(), table);

            Set<String> allConstraints = new HashSet<>();
            allConstraints.addAll(sourceConstraints.keySet());
            allConstraints.addAll(targetConstraints.keySet());

            for (String constraint : allConstraints) {
                String sourceType = sourceConstraints.get(constraint);
                String targetType = targetConstraints.get(constraint);

                String objectName = table + "." + constraint;

                if (sourceType == null) {
                    driftItems.add(new DriftItem(
                            "Compatibility",
                            objectName,
                            "exists",
                            false,
                            true,
                            DriftStatus.DIFFER,
                            DriftSeverity.WARN,
                            "Low",
                            String.format("Constraint '%s' exists in target but not in source", objectName)
                    ));
                    differCount++;
                } else if (targetType == null) {
                    DriftSeverity severity = sourceType.equals("PRIMARY KEY") || sourceType.equals("UNIQUE")
                            ? DriftSeverity.ERROR
                            : DriftSeverity.WARN;
                    String riskLevel = sourceType.equals("PRIMARY KEY") || sourceType.equals("UNIQUE")
                            ? "High"
                            : "Medium";
                    driftItems.add(new DriftItem(
                            "Compatibility",
                            objectName,
                            "exists",
                            true,
                            false,
                            DriftStatus.DIFFER,
                            severity,
                            riskLevel,
                            String.format("Constraint '%s' (%s) exists in source but missing in target", objectName, sourceType)
                    ));
                    differCount++;
                } else if (!sourceType.equals(targetType)) {
                    driftItems.add(new DriftItem(
                            "Compatibility",
                            objectName,
                            "type",
                            sourceType,
                            targetType,
                            DriftStatus.DIFFER,
                            DriftSeverity.ERROR,
                            "High",
                            String.format("Constraint '%s' type mismatch: %s vs %s", objectName, sourceType, targetType)
                    ));
                    differCount++;
                } else {
                    // Add MATCH item (if under limit)
                    if (matchCount < MAX_MATCH_ITEMS) {
                        driftItems.add(new DriftItem(
                                "Compatibility",
                                objectName,
                                "type",
                                sourceType,
                                targetType,
                                DriftStatus.MATCH,
                                DriftSeverity.INFO,
                                null,
                                String.format("Constraint '%s' (%s) matches in both environments", objectName, sourceType)
                        ));
                    }
                    matchCount++;
                }
            }
        }

        return new DriftSection(
                "Constraints",
                "Primary keys, foreign keys, unique constraints",
                SectionAvailability.createAvailable(),
                driftItems,
                matchCount,
                differCount,
                0
        );
    }

    /**
     * Compare indexes between source and target
     */
    public DriftSection compareIndexesSection(
            DbConnectContextDto sourceCtx,
            DbConnectContextDto targetCtx,
            EnvironmentCapabilityMatrix sourceCapabilities,
            EnvironmentCapabilityMatrix targetCapabilities,
            Set<String> commonTables) {

        if (!sourceCapabilities.indexes().available() || !targetCapabilities.indexes().available()) {
            String reason = !sourceCapabilities.indexes().available()
                    ? sourceCapabilities.indexes().message()
                    : targetCapabilities.indexes().message();

            return new DriftSection(
                    "Indexes",
                    "Table indexes and their definitions",
                    SectionAvailability.createUnavailable(
                            reason,
                            "read access to pg_catalog.pg_indexes",
                            "Cannot detect index drift; performance issues may be undetectable"
                    ),
                    Collections.emptyList(),
                    0, 0, 0
            );
        }

        DataSource sourceDs = dataSourceFactory.build(sourceCtx);
        DataSource targetDs = dataSourceFactory.build(targetCtx);

        List<DriftItem> driftItems = new ArrayList<>();
        int matchCount = 0;
        int differCount = 0;

        for (String table : commonTables) {
            Map<String, String> sourceIndexes = getIndexes(sourceDs, sourceCtx.schema(), table);
            Map<String, String> targetIndexes = getIndexes(targetDs, targetCtx.schema(), table);

            Set<String> allIndexes = new HashSet<>();
            allIndexes.addAll(sourceIndexes.keySet());
            allIndexes.addAll(targetIndexes.keySet());

            for (String index : allIndexes) {
                String sourceDef = sourceIndexes.get(index);
                String targetDef = targetIndexes.get(index);

                String objectName = table + "." + index;

                if (sourceDef == null) {
                    driftItems.add(new DriftItem(
                            "Performance",
                            objectName,
                            "exists",
                            false,
                            true,
                            DriftStatus.DIFFER,
                            DriftSeverity.WARN,
                            "Low",
                            String.format("Index '%s' exists in target but not in source", objectName)
                    ));
                    differCount++;
                } else if (targetDef == null) {
                    // Determine risk level based on index characteristics
                    String riskLevel = "Medium";
                    if (sourceDef.contains("UNIQUE") || sourceDef.contains("PRIMARY KEY")) {
                        riskLevel = "High";
                    } else if (sourceDef.contains(",") || sourceDef.contains(" btree (")) {
                        riskLevel = "Medium";  // composite or standard b-tree
                    }
                    
                    driftItems.add(new DriftItem(
                            "Performance",
                            objectName,
                            "exists",
                            true,
                            false,
                            DriftStatus.DIFFER,
                            DriftSeverity.WARN,
                            riskLevel,
                            String.format("Index '%s' exists in source but missing in target", objectName)
                    ));
                    differCount++;
                } else if (!sourceDef.equals(targetDef)) {
                    driftItems.add(new DriftItem(
                            "Performance",
                            objectName,
                            "definition",
                            sourceDef,
                            targetDef,
                            DriftStatus.DIFFER,
                            DriftSeverity.WARN,
                            "Medium",
                            String.format("Index '%s' definition differs", objectName)
                    ));
                    differCount++;
                } else {
                    // Add MATCH item (if under limit)
                    if (matchCount < MAX_MATCH_ITEMS) {
                        driftItems.add(new DriftItem(
                                "Performance",
                                objectName,
                                "definition",
                                sourceDef,
                                targetDef,
                                DriftStatus.MATCH,
                                DriftSeverity.INFO,
                                null,
                                String.format("Index '%s' matches in both environments", objectName)
                        ));
                    }
                    matchCount++;
                }
            }
        }

        return new DriftSection(
                "Indexes",
                "Table indexes and their definitions",
                SectionAvailability.createAvailable(),
                driftItems,
                matchCount,
                differCount,
                0
        );
    }

    // Helper methods

    /**
     * List tables for a given connection context, honoring specific table filters.
     */
    public Set<String> listTables(DbConnectContextDto ctx, List<String> specificTables) {
        DataSource ds = dataSourceFactory.build(ctx);
        return getTables(ds, ctx.schema(), specificTables);
    }

    private Set<String> getTables(DataSource ds, String schema, List<String> specificTables) {
        Set<String> tables = new HashSet<>();
        String sql = specificTables != null && !specificTables.isEmpty()
                ? "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name = ANY(?)"
                : "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            if (specificTables != null && !specificTables.isEmpty()) {
                ps.setArray(2, conn.createArrayOf("text", specificTables.toArray()));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get tables: {}", e.getMessage());
        }
        return tables;
    }

    private Map<String, ColumnInfo> getColumns(DataSource ds, String schema, String table) {
        Map<String, ColumnInfo> columns = new HashMap<>();
        String sql = """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    columns.put(columnName, new ColumnInfo(
                            rs.getString("data_type"),
                            rs.getString("is_nullable").equals("YES"),
                            rs.getString("column_default")
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get columns for {}.{}: {}", schema, table, e.getMessage());
        }
        return columns;
    }

    private Map<String, String> getConstraints(DataSource ds, String schema, String table) {
        Map<String, String> constraints = new HashMap<>();
        String sql = """
                SELECT constraint_name, constraint_type
                FROM information_schema.table_constraints
                WHERE table_schema = ? AND table_name = ?
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    constraints.put(rs.getString("constraint_name"), rs.getString("constraint_type"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get constraints for {}.{}: {}", schema, table, e.getMessage());
        }
        return constraints;
    }

    private Map<String, String> getIndexes(DataSource ds, String schema, String table) {
        Map<String, String> indexes = new HashMap<>();
        String sql = """
                SELECT indexname, indexdef
                FROM pg_catalog.pg_indexes
                WHERE schemaname = ? AND tablename = ?
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    indexes.put(rs.getString("indexname"), rs.getString("indexdef"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get indexes for {}.{}: {}", schema, table, e.getMessage());
        }
        return indexes;
    }

    private record ColumnInfo(String dataType, boolean isNullable, String columnDefault) {}
}

