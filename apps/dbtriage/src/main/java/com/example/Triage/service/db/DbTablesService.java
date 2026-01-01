package com.example.Triage.service.db;

import com.example.Triage.model.response.DbTablesResponse;
import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContext;
import com.example.Triage.model.dto.TableInfo;
import com.example.Triage.model.response.DbTableSearchResponse;
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

        try (var c = ds.getConnection()) {

            List<TableInfo> tables = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(DbQueries.GET_TABLES)) {
                ps.setString(1, schema);
                try (var rs = ps.executeQuery()) {
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
        var ds = buildDataSource(ctx);

        try (var c = ds.getConnection()) {
            List<TableInfo> matches = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(DbQueries.GET_TABLES_BY_NAME)) {
                ps.setString(1, schema);
                ps.setString(2, "%" + query + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("table_name");
                        Long rowCount = rs.getLong("estimated_row_count");
                        String owner = rs.getString("tableowner");
                        matches.add(new TableInfo(name, rowCount, owner));
                    }
                }
            }

            return new DbTableSearchResponse(schema, query, matches);
        }
    }

    private DataSource buildDataSource(DbConnectContext ctx) {
        var ds = new PGSimpleDataSource();
        var sslMode = (ctx.sslMode() == null || ctx.sslMode().isBlank()) ? "require" : ctx.sslMode();

        var url = String.format(
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
