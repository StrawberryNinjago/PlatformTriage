package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbConstraint;
import com.example.Triage.model.dto.DbTableColumn;
import com.example.Triage.model.response.DbConstraintsResponse;
import com.example.Triage.model.response.DbTableIntrospectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbIntrospectService {

    private final DbDataSourceFactory dataSourceFactory;
    private final DbIndexService indexService;

    public DbConstraintsResponse listConstraints(DbConnectContextDto ctx, String schema, String table)
            throws SQLException {
        DataSource ds = dataSourceFactory.build(ctx);
        try (var conn = ds.getConnection()) {
            var constraints = queryConstraints(conn, schema, table);
            List<DbConstraint> converted = new ArrayList<>();
            for (var c : constraints) {
                converted.add(new DbConstraint(table, c.name(), c.type(), c.columns(), c.definition()));
            }
            return new DbConstraintsResponse(schema, table, converted);
        }
    }

    public DbTableIntrospectResponse introspectTable(DbConnectContextDto ctx, String schema, String table)
            throws SQLException {
        DataSource ds = dataSourceFactory.build(ctx);
        try (var conn = ds.getConnection()) {
            var columns = queryColumns(conn, schema, table);
            var indexes = indexService.listIndexes(ctx, schema, table).indexes();
            var constraints = queryConstraints(conn, schema, table);
            var owner = queryTableOwner(conn, schema, table);
            var currentUser = queryCurrentUser(conn);
            var flywayInfo = queryFlywayInfoForTable(conn, schema, table);
            return new DbTableIntrospectResponse(schema, table, owner, currentUser, columns, indexes, constraints, flywayInfo);
        }
    }

    private List<DbTableColumn> queryColumns(Connection conn, String schema, String table)
            throws SQLException {
        String sql = """
                SELECT column_name, ordinal_position, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;

        List<DbTableColumn> out = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DbTableColumn(
                            rs.getString("column_name"),
                            rs.getInt("ordinal_position"),
                            rs.getString("data_type"),
                            "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            rs.getString("column_default")
                    ));
                }
            }
        }

        return out;
    }

    private List<DbConstraint> queryConstraints(Connection conn, String schema, String table)
            throws SQLException {

        List<DbConstraint> out = new ArrayList<>();

        try (var ps = conn.prepareStatement(DbQueries.GET_CONSTRAINTS)) {
            ps.setString(1, schema);
            ps.setString(2, table);

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    String contype = rs.getString("contype");
                    String type = mapConstraintType(contype);
                    String definition = rs.getString("definition");

                    List<String> cols = new ArrayList<>();
                    var arr = rs.getArray("columns");
                    if (arr != null) {
                        String[] raw = (String[]) arr.getArray();
                        if (raw != null) {
                            for (String s : raw)
                                if (s != null)
                                    cols.add(s);
                        }
                    }

                    out.add(new DbConstraint(table, name, type, cols, definition));
                }
            }
        }
        return out;
    }

    private String mapConstraintType(String contype) {
        if (contype == null)
            return "UNKNOWN";
        return switch (contype) {
            case "p" -> "PRIMARY KEY";
            case "u" -> "UNIQUE";
            case "f" -> "FOREIGN KEY";
            case "c" -> "CHECK";
            case "x" -> "EXCLUSION";
            default -> "OTHER(" + contype + ")";
        };
    }

    private String queryTableOwner(Connection conn, String schema, String table) throws SQLException {
        String sql = "SELECT tableowner FROM pg_tables WHERE schemaname = ? AND tablename = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tableowner");
                }
            }
        }
        return null;
    }

    private String queryCurrentUser(Connection conn) throws SQLException {
        String sql = "SELECT current_user";
        try (var ps = conn.prepareStatement(sql)) {
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private DbTableIntrospectResponse.FlywayMigrationInfo queryFlywayInfoForTable(Connection conn, String schema, String table) {
        try {
            // First check if flyway_schema_history table exists
            String checkSql = "SELECT to_regclass('public.flyway_schema_history') AS flyway_table";
            try (var ps = conn.prepareStatement(checkSql)) {
                try (var rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getString("flyway_table") == null) {
                        return null;
                    }
                }
            }

            // Look for migrations that might have created this table
            // We look for script names that contain the table name
            String sql = """
                SELECT version, description, script, installed_by, installed_on
                FROM public.flyway_schema_history
                WHERE success = true
                  AND (script ILIKE ? OR description ILIKE ?)
                ORDER BY installed_rank DESC
                LIMIT 1
                """;
            
            try (var ps = conn.prepareStatement(sql)) {
                String searchPattern = "%" + table + "%";
                ps.setString(1, searchPattern);
                ps.setString(2, searchPattern);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return DbTableIntrospectResponse.FlywayMigrationInfo.builder()
                            .version(rs.getString("version"))
                            .description(rs.getString("description"))
                            .installedBy(rs.getString("installed_by"))
                            .installedOn(rs.getTimestamp("installed_on") != null ? 
                                rs.getTimestamp("installed_on").toString() : null)
                            .build();
                    }
                }
            }
        } catch (Exception e) {
            // If anything fails, just return null (Flyway not configured or table not found)
            return null;
        }
        return null;
    }
}
