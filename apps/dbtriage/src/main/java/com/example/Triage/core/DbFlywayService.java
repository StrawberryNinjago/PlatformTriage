package com.example.Triage.core;

import com.example.Triage.model.response.DbFlywayHealthResponse;
import com.example.Triage.model.response.DbFlywayHealthResponse.FlywayStatus;
import com.example.Triage.model.response.DbFlywayHealthResponse.LatestApplied;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;

@Service
public class DbFlywayService {

    public DbFlywayHealthResponse getFlywayHealth(DbConnectContext ctx) throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            boolean historyExists = flywayHistoryExists(c);
            
            if (!historyExists) {
                return new DbFlywayHealthResponse(
                    FlywayStatus.NOT_CONFIGURED,
                    false,
                    null,
                    0,
                    "Flyway schema history table not found. Flyway may not be configured."
                );
            }

            LatestApplied latest = queryLatestApplied(c);
            int failedCount = queryFlywayFailedCount(c);

            FlywayStatus status;
            String message;

            if (failedCount > 0) {
                status = FlywayStatus.FAILED;
                message = String.format("Found %d failed migration(s). Requires attention.", failedCount);
            } else if (latest == null) {
                status = FlywayStatus.DEGRADED;
                message = "No successful migrations found.";
            } else {
                status = FlywayStatus.HEALTHY;
                message = String.format("Latest migration: %s - %s", 
                    latest.version(), latest.description());
            }

            return new DbFlywayHealthResponse(status, historyExists, latest, failedCount, message);
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

    private LatestApplied queryLatestApplied(Connection c) throws SQLException {
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
            OffsetDateTime installedOn = ts != null 
                ? ts.toInstant().atOffset(OffsetDateTime.now().getOffset()) 
                : null;

            return new LatestApplied(rank, version, description, script, installedOn);
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

