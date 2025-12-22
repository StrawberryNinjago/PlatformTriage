package com.example.Triage.core;

import com.example.Triage.model.response.DbSummaryResponse;
import com.example.Triage.model.response.DbTableIntrospectResponse;
import com.example.Triage.core.DbConnectContext;
import com.example.Triage.model.response.DbIndexResponse;
import com.example.Triage.model.response.DbConstraintsResponse;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DbSummaryService {

    // MVP: hardcode 0–5 important tables; later make configurable
    private static final List<String> IMPORTANT_TABLES = List.of(
            "cart",
            "line_of_service",
            "cart_item",
            "order_item",
            "shipping_info");

    public DbSummaryResponse getSummary(DbConnectContext ctx) throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            DbSummaryResponse.DbIdentity identity = queryIdentity(c);
            boolean flywayExists = flywayHistoryExists(c);

            DbSummaryResponse.FlywaySummary flyway = new DbSummaryResponse.FlywaySummary(
                    flywayExists,
                    flywayExists ? queryLatestApplied(c) : null,
                    flywayExists ? queryFlywayFailedCount(c) : 0);

            int publicTableCount = queryPublicTableCount(c);
            List<DbSummaryResponse.PublicSchemaSummary.TableExistence> important = queryImportantTablesExistence(c);

            DbSummaryResponse.PublicSchemaSummary schema = new DbSummaryResponse.PublicSchemaSummary(
                    publicTableCount,
                    important);

            return new DbSummaryResponse(identity, flyway, schema);
        }
    }

    public DbIndexResponse listIndexes(DbConnectContext ctx, String schema, String table) throws SQLException {
        var ds = buildDataSource(ctx);
        try (var conn = ds.getConnection()) {
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

            List<DbIndexResponse.DbIndex> out = new ArrayList<>();
            try (var ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, table);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new DbIndexResponse.DbIndex(
                                rs.getString("indexname"),
                                rs.getBoolean("is_unique"),
                                rs.getBoolean("is_primary"),
                                rs.getString("method"),
                                rs.getString("indexdef")));
                    }
                }
            }
            return new DbIndexResponse(schema, table, out);
        }
    }

    private DataSource buildDataSource(DbConnectContext ctx) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s", ctx.host(), ctx.port(), ctx.database(), ctx.sslMode());
        ds.setUrl(url);
        ds.setUser(ctx.username());
        ds.setPassword(ctx.password());
        ds.setConnectTimeout(5);
        ds.setLoginTimeout(5);
        return ds;
    }

    private DbSummaryResponse.DbIdentity queryIdentity(Connection c) throws SQLException {
        String sql = """
                select
                  current_database()  as db,
                  current_user        as usr,
                  inet_server_addr()  as server_addr,
                  inet_server_port()  as server_port,
                  version()           as server_version,
                  now()               as server_time
                """;
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            rs.next();
            String db = rs.getString("db");
            String usr = rs.getString("usr");
            String addr = rs.getString("server_addr");
            int port = rs.getInt("server_port");
            String ver = rs.getString("server_version");

            Timestamp ts = rs.getTimestamp("server_time");
            OffsetDateTime serverTime = ts != null ? ts.toInstant().atOffset(OffsetDateTime.now().getOffset()) : null;

            return new DbSummaryResponse.DbIdentity(db, usr, addr, port, ver, serverTime);
        }
    }

    private boolean flywayHistoryExists(Connection c) throws SQLException {
        String sql = "select to_regclass('public.flyway_schema_history') as flyway_table";
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString("flyway_table") != null;
        }
    }

    private DbSummaryResponse.FlywaySummary.LatestApplied queryLatestApplied(Connection c) throws SQLException {
        String sql = """
                select installed_rank, version, description, script, installed_on
                from public.flyway_schema_history
                where success = true
                order by installed_rank desc
                limit 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (!rs.next())
                return null;

            Integer rank = (Integer) rs.getObject("installed_rank");
            String version = rs.getString("version");
            String description = rs.getString("description");
            String script = rs.getString("script");

            Timestamp ts = rs.getTimestamp("installed_on");
            OffsetDateTime installedOn = ts != null ? ts.toInstant().atOffset(OffsetDateTime.now().getOffset()) : null;

            return new DbSummaryResponse.FlywaySummary.LatestApplied(rank, version, description, script, installedOn);
        }
    }

    private int queryFlywayFailedCount(Connection c) throws SQLException {
        String sql = "select count(*) as cnt from public.flyway_schema_history where success = false";
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("cnt");
        }
    }

    private int queryPublicTableCount(Connection c) throws SQLException {
        String sql = """
                select count(*) as cnt
                from information_schema.tables
                where table_schema = 'public'
                  and table_type = 'BASE TABLE'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("cnt");
        }
    }

    private List<DbSummaryResponse.PublicSchemaSummary.TableExistence> queryImportantTablesExistence(Connection c)
            throws SQLException {

        List<DbSummaryResponse.PublicSchemaSummary.TableExistence> out = new ArrayList<>();

        String sql = """
                select exists(
                  select 1
                  from information_schema.tables
                  where table_schema = 'public'
                    and table_name = ?
                ) as present
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String t : IMPORTANT_TABLES) {
                ps.setString(1, t);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    boolean exists = rs.getBoolean("present");
                    out.add(new DbSummaryResponse.PublicSchemaSummary.TableExistence(t, exists));
                }
            }
        }
        return out;
    }

    public DbConstraintsResponse listConstraints(DbConnectContext ctx, String schema, String table)
            throws SQLException {
        var ds = buildDataSource(ctx);
        try (var conn = ds.getConnection()) {
            var constraints = queryConstraints(conn, schema, table);
            // Convert from DbTableIntrospectResponse.DbConstraint to
            // DbConstraintsResponse.DbConstraint
            List<DbConstraintsResponse.DbConstraint> converted = new ArrayList<>();
            for (var c : constraints) {
                converted.add(new DbConstraintsResponse.DbConstraint(c.name(), c.type(), c.columns(), c.definition()));
            }
            return new DbConstraintsResponse(schema, table, converted);
        }
    }

    public DbTableIntrospectResponse introspectTable(DbConnectContext ctx, String schema, String table)
            throws SQLException {
        var ds = buildDataSource(ctx);
        try (var conn = ds.getConnection()) {
            var indexes = queryIndexes(conn, schema, table);
            var constraints = queryConstraints(conn, schema, table);
            return new DbTableIntrospectResponse(schema, table, indexes, constraints);
        }
    }

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

        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (var rs = ps.executeQuery()) {
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

        try (var ps = conn.prepareStatement(sql)) {
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

                    out.add(new DbTableIntrospectResponse.DbConstraint(name, type, cols, definition));
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

}
