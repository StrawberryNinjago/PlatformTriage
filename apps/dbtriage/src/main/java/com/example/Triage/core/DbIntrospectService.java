package com.example.Triage.core;

import com.example.Triage.model.response.DbFindResponse;
import com.example.Triage.model.response.DbTableIntrospectResponse;
import com.example.Triage.core.DbConnectContext;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DbIntrospectService {

    public DbTableIntrospectResponse introspectTable(DbConnectContext ctx, String schema, String table)
            throws SQLException {
        DataSource ds = buildDataSource(ctx);
        try (Connection conn = ds.getConnection()) {
            List<DbTableIntrospectResponse.DbIndex> indexes = queryIndexes(conn, schema, table);
            List<DbTableIntrospectResponse.DbConstraint> constraints = queryConstraints(conn, schema, table);
            return new DbTableIntrospectResponse(schema, table, indexes, constraints);
        }
    }

    public DbFindResponse findByNameContains(DbConnectContext ctx, String schema, String nameContains)
            throws SQLException {
        DataSource ds = buildDataSource(ctx);
        try (Connection conn = ds.getConnection()) {
            List<DbFindResponse.FoundIndex> indexes = findIndexes(conn, schema, nameContains);
            List<DbFindResponse.FoundConstraint> constraints = findConstraints(conn, schema, nameContains);
            return new DbFindResponse(schema, nameContains, indexes, constraints);
        }
    }

    // ---------- DataSource builder (self-contained) ----------
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

    // ---------- Indexes for a specific table ----------
    private List<DbTableIntrospectResponse.DbIndex> queryIndexes(Connection conn, String schema, String table)
            throws SQLException {
        String sql = """
                select
                  i.indexname,
                  ix.indisunique as is_unique,
                  ix.indisprimary as is_primary,
                  am.amname as method,
                  i.indexdef
                from pg_indexes i
                join pg_namespace ns on ns.nspname = i.schemaname
                join pg_class t on t.relname = i.tablename and t.relnamespace = ns.oid
                join pg_class ic on ic.relname = i.indexname
                join pg_index ix on ix.indexrelid = ic.oid
                join pg_am am on am.oid = ic.relam
                where i.schemaname = ?
                  and i.tablename = ?
                order by i.indexname
                """;

        List<DbTableIntrospectResponse.DbIndex> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DbTableIntrospectResponse.DbIndex(
                            rs.getString("indexname"),
                            rs.getBoolean("is_unique"),
                            rs.getBoolean("is_primary"),
                            rs.getString("method"),
                            rs.getString("indexdef")));
                }
            }
        }
        return out;
    }

    // ---------- Constraints for a specific table ----------
    private List<DbTableIntrospectResponse.DbConstraint> queryConstraints(Connection conn, String schema, String table)
            throws SQLException {
        String sql = """
                select
                  c.conname as constraint_name,
                  c.contype as contype,
                  pg_get_constraintdef(c.oid, true) as definition,
                  array_remove(array_agg(a.attname order by u.ord), null) as columns
                from pg_constraint c
                join pg_class t on t.oid = c.conrelid
                join pg_namespace ns on ns.oid = t.relnamespace
                left join lateral unnest(c.conkey) with ordinality as u(attnum, ord) on true
                left join pg_attribute a on a.attrelid = t.oid and a.attnum = u.attnum
                where ns.nspname = ?
                  and t.relname = ?
                group by c.conname, c.contype, c.oid
                order by c.conname
                """;

        List<DbTableIntrospectResponse.DbConstraint> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DbTableIntrospectResponse.DbConstraint(
                            rs.getString("constraint_name"),
                            mapConstraintType(rs.getString("contype")),
                            readTextArray(rs.getArray("columns")),
                            rs.getString("definition")));
                }
            }
        }
        return out;
    }

    // ---------- Find indexes by name substring ----------
    private List<DbFindResponse.FoundIndex> findIndexes(Connection conn, String schema, String nameContains)
            throws SQLException {
        // Use ILIKE for case-insensitive search
        String sql = """
                select
                  i.tablename,
                  i.indexname,
                  ix.indisunique as is_unique,
                  ix.indisprimary as is_primary,
                  i.indexdef
                from pg_indexes i
                join pg_namespace ns on ns.nspname = i.schemaname
                join pg_class t on t.relname = i.tablename and t.relnamespace = ns.oid
                join pg_class ic on ic.relname = i.indexname
                join pg_index ix on ix.indexrelid = ic.oid
                where i.schemaname = ?
                  and i.indexname ilike ?
                order by i.tablename, i.indexname
                """;

        List<DbFindResponse.FoundIndex> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, "%" + nameContains + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DbFindResponse.FoundIndex(
                            rs.getString("tablename"),
                            rs.getString("indexname"),
                            rs.getBoolean("is_unique"),
                            rs.getBoolean("is_primary"),
                            rs.getString("indexdef")));
                }
            }
        }
        return out;
    }

    // ---------- Find constraints by name substring ----------
    private List<DbFindResponse.FoundConstraint> findConstraints(Connection conn, String schema, String nameContains)
            throws SQLException {
        String sql = """
                select
                  t.relname as table_name,
                  c.conname as constraint_name,
                  c.contype as contype,
                  pg_get_constraintdef(c.oid, true) as definition,
                  array_remove(array_agg(a.attname order by u.ord), null) as columns
                from pg_constraint c
                join pg_class t on t.oid = c.conrelid
                join pg_namespace ns on ns.oid = t.relnamespace
                left join lateral unnest(c.conkey) with ordinality as u(attnum, ord) on true
                left join pg_attribute a on a.attrelid = t.oid and a.attnum = u.attnum
                where ns.nspname = ?
                  and c.conname ilike ?
                group by t.relname, c.conname, c.contype, c.oid
                order by t.relname, c.conname
                """;

        List<DbFindResponse.FoundConstraint> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, "%" + nameContains + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DbFindResponse.FoundConstraint(
                            rs.getString("table_name"),
                            rs.getString("constraint_name"),
                            mapConstraintType(rs.getString("contype")),
                            readTextArray(rs.getArray("columns")),
                            rs.getString("definition")));
                }
            }
        }
        return out;
    }

    private List<String> readTextArray(Array arr) throws SQLException {
        List<String> out = new ArrayList<>();
        if (arr == null)
            return out;
        Object o = arr.getArray();
        if (o instanceof String[] raw) {
            for (String s : raw)
                if (s != null)
                    out.add(s);
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
}