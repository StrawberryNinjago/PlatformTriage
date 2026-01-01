package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContext;
import com.example.Triage.model.dto.LatestApplied;
import com.example.Triage.model.enums.FlywayStatus;
import com.example.Triage.model.response.DbFlywayHealthResponse;
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
                        "Flyway schema history table not found. Flyway may not be configured.");
            }

            var latest = queryLatestApplied(c);
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
        try (PreparedStatement ps = c.prepareStatement(DbQueries.GET_FLYWAY_HISTORY);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString("flyway_table") != null;
        }
    }

    private LatestApplied queryLatestApplied(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                DbQueries.GET_LATEST_APPLIED);
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
        try (PreparedStatement ps = c.prepareStatement(
                DbQueries.GET_FLYWAY_FAILED_COUNT);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("cnt");
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
