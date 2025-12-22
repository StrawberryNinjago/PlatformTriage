package com.example.Triage.core;

import com.example.Triage.model.response.DbTablesResponse;
import com.example.Triage.model.response.DbTablesResponse.TableInfo;
import com.example.Triage.model.response.DbTableSearchResponse;
import com.example.Triage.model.response.DbTableSearchResponse.TableMatch;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class DbTablesService {

    public DbTablesResponse listTables(DbConnectContext ctx, String schema) throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            String sql = """
                    SELECT
                      c.relname AS table_name,
                      COALESCE(c.reltuples::bigint, 0) AS estimated_row_count,
                      r.rolname AS table_owner
                    FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
                    WHERE n.nspname = ?
                      AND c.relkind = 'r'
                      AND c.relname NOT LIKE 'pg_%'
                      AND c.relname NOT LIKE 'sql_%'
                    ORDER BY c.relname
                    """;

            List<TableInfo> tables = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("table_name");
                        long rowCount = rs.getLong("estimated_row_count");
                        String owner = rs.getString("table_owner");
                        tables.add(new TableInfo(name, rowCount, owner));
                    }
                }
            }
            return new DbTablesResponse(schema, tables);
        }
    }

    public DbTableSearchResponse searchTables(DbConnectContext ctx, String schema, String query) throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            String sql = """
                    SELECT
                      t.table_name,
                      COALESCE(pg_class.reltuples::bigint, 0) AS estimated_row_count,
                      pg_tables.tableowner
                    FROM information_schema.tables t
                    LEFT JOIN pg_catalog.pg_class ON pg_class.relname = t.table_name
                    LEFT JOIN pg_catalog.pg_tables ON pg_tables.schemaname = t.table_schema
                        AND pg_tables.tablename = t.table_name
                    WHERE t.table_schema = ?
                      AND t.table_type = 'BASE TABLE'
                      AND t.table_name NOT LIKE 'pg_%'
                      AND t.table_name NOT LIKE 'sql_%'
                      AND t.table_schema != 'information_schema'
                      AND t.table_name ILIKE ?
                    ORDER BY t.table_name
                    """;

            List<TableMatch> matches = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, "%" + query + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("table_name");
                        Long rowCount = rs.getLong("estimated_row_count");
                        String owner = rs.getString("tableowner");
                        matches.add(new TableMatch(name, rowCount, owner));
                    }
                }
            }

            return new DbTableSearchResponse(schema, query, matches);
        }
    }

    private DataSource buildDataSource(DbConnectContext ctx) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        String sslMode = (ctx.sslMode() == null || ctx.sslMode().isBlank()) ? "require" : ctx.sslMode();

        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s",
                ctx.host(), ctx.port(), ctx.database(), sslMode);

        ds.setUrl(url);
        ds.setUser(ctx.username());
        ds.setPassword(ctx.password());
        ds.setConnectTimeout(5);
        ds.setLoginTimeout(5);
        return ds;
    }
}
