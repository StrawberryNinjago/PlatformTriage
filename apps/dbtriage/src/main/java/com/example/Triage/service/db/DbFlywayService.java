package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.exception.FlywayHealthCheckException;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbIdentityDto;
import com.example.Triage.model.dto.FlywayHistoryRowDto;
import com.example.Triage.model.dto.WarningMessageDto;
import com.example.Triage.model.dto.LatestAppliedDto;
import com.example.Triage.model.enums.FlywayStatus;
import com.example.Triage.model.response.DbFlywayHealthResponse;
import com.example.Triage.model.dto.FlywayInstalledBySummaryDto;
import com.example.Triage.model.dto.FlywaySummaryDto;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@Slf4j
public class DbFlywayService {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int SUMMARY_LIMIT = 10;

    public DbFlywayHealthResponse getFlywayHealth(DbConnectContextDto ctx) {
        log.info("#getFlywayHealth: Getting flyway health for connectionId={}", ctx.id());

        try (var conn = buildDataSource(ctx).getConnection()) {

            var identity = queryIdentity(conn, ctx.schema());
            boolean historyExists = flywayHistoryExists(conn);

            if (!historyExists) {
                return notConfigured(identity, ctx.username());
            }

            var flywaySummary = buildFlywaySummary(conn);
            var evaluation = evaluateFlyway(flywaySummary);
            var warnings = computeWarnings(flywaySummary, identity);

            log.info("#getFlywayHealth: status={}, message={}, warnings={}",
                    evaluation.status(), evaluation.message(), warnings.size());

            return DbFlywayHealthResponse.builder()
                    .status(evaluation.status())
                    .message(evaluation.message())
                    .identity(identity)
                    .flywaySummary(flywaySummary)
                    .expectedUser(ctx.username())
                    .warnings(warnings)
                    .build();

        } catch (SQLException e) {
            log.error("#getFlywayHealth: SQL failure for connectionId={}", ctx.id(), e);
            throw new FlywayHealthCheckException("DB_FLYWAY_HEALTH_CHECK_SQL_FAILED", e);
        } catch (Exception e) {
            log.error("#getFlywayHealth: Unexpected failure for connectionId={}", ctx.id(), e);
            throw new FlywayHealthCheckException("DB_FLYWAY_HEALTH_CHECK_FAILED", e);
        }
    }

    public List<FlywayHistoryRowDto> getFlywayHistory(DbConnectContextDto ctx, int limit) {
        log.info("#getFlywayHistory: Getting flyway history for connectionId={}, limit={}", ctx.id(), limit);
        int safeLimit = Math.min(Math.max(limit, 1), 200); // 1..200

        try (var conn = buildDataSource(ctx).getConnection()) {
            if (!flywayHistoryExists(conn)) {
                return List.of();
            }
            return queryFlywayHistoryRecent(conn, safeLimit);
        } catch (SQLException e) {
            throw new FlywayHealthCheckException("DB_FLYWAY_HISTORY_QUERY_FAILED", e);
        }
    }

    private List<FlywayHistoryRowDto> queryFlywayHistoryRecent(Connection conn, int limit) throws SQLException {
        log.info("#queryFlywayHistoryRecent: Querying flyway history for connection with limit={}", limit);
        try (var ps = conn.prepareStatement(DbQueries.GET_FLYWAY_HISTORY_RECENT)) {
            ps.setInt(1, limit);

            try (var rs = ps.executeQuery()) {
                List<FlywayHistoryRowDto> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(FlywayHistoryRowDto.builder()
                            .installedRank((Integer) rs.getObject("installed_rank"))
                            .version(rs.getString("version"))
                            .description(rs.getString("description"))
                            .type(rs.getString("type"))
                            .script(rs.getString("script"))
                            .installedBy(rs.getString("installed_by"))
                            .installedOn(getOffsetDateTime(rs, "installed_on"))
                            .executionTimeMs((Integer) rs.getObject("execution_time"))
                            .success((Boolean) rs.getObject("success"))
                            .build());
                }
                return out;
            } catch (SQLException e) {
                log.error("#queryFlywayHistoryRecent: SQL failure for connection with limit={}", limit, e);
                throw new FlywayHealthCheckException("DB_FLYWAY_HISTORY_QUERY_FAILED", e);
            }
        } catch (SQLException e) {
            log.error("#queryFlywayHistoryRecent: SQL failure for connection with limit={}", limit, e);
            throw new FlywayHealthCheckException("DB_FLYWAY_HISTORY_QUERY_FAILED", e);
        } catch (Exception e) {
            log.error("#queryFlywayHistoryRecent: Unexpected failure for connection with limit={}", limit, e);
            throw new FlywayHealthCheckException("DB_FLYWAY_HISTORY_QUERY_FAILED", e);
        }
    }

    private DbFlywayHealthResponse notConfigured(DbIdentityDto identity, String expectedUser) {
        var flywaySummary = FlywaySummaryDto.builder()
                .historyTableExists(false)
                .latestApplied(null)
                .failedCount(0)
                .installedBySummary(List.of())
                .build();

        return DbFlywayHealthResponse.builder()
                .status(FlywayStatus.NOT_CONFIGURED)
                .message("Flyway schema history table not found. Flyway may not be configured.")
                .identity(identity)
                .flywaySummary(flywaySummary)
                .expectedUser(expectedUser)
                .warnings(List.of())
                .build();
    }

    private FlywaySummaryDto buildFlywaySummary(Connection conn) throws SQLException {
        var latest = queryLatestApplied(conn);
        int failedCount = queryFlywayFailedCount(conn);
        var installedBySummary = queryInstalledBySummary(conn);

        return FlywaySummaryDto.builder()
                .historyTableExists(true)
                .latestApplied(latest)
                .failedCount(failedCount)
                .installedBySummary(installedBySummary)
                .build();
    }

    private FlywayEvaluation evaluateFlyway(FlywaySummaryDto summary) {
        if (!summary.historyTableExists()) {
            return new FlywayEvaluation(FlywayStatus.NOT_CONFIGURED,
                    "Flyway schema history table not found. Flyway may not be configured.");
        }

        if (summary.failedCount() > 0) {
            return new FlywayEvaluation(FlywayStatus.FAILED,
                    String.format("Found %d failed migration(s). Requires attention.", summary.failedCount()));
        }

        if (summary.latestApplied() == null) {
            return new FlywayEvaluation(FlywayStatus.DEGRADED, "No successful migrations found.");
        }

        var latest = summary.latestApplied();
        return new FlywayEvaluation(FlywayStatus.HEALTHY,
                String.format("Latest migration: %s - %s", latest.version(), latest.description()));
    }

    private List<FlywayInstalledBySummaryDto> queryInstalledBySummary(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(DbQueries.GET_FLYWAY_INSTALLED_BY_SUMMARY);
                var rs = ps.executeQuery()) {

            List<FlywayInstalledBySummaryDto> out = new ArrayList<>();

            while (rs.next()) {
                out.add(FlywayInstalledBySummaryDto.builder()
                        .installedBy(rs.getString("installed_by"))
                        .appliedCount(rs.getInt("applied_count"))
                        .lastSeen(getOffsetDateTime(rs, "last_seen"))
                        .build());
            }

            if (out.size() > SUMMARY_LIMIT) {
                return out.subList(0, SUMMARY_LIMIT);
            }
            return out;
        }
    }

    private DbIdentityDto queryIdentity(Connection c, String schema) throws SQLException {
        try (var ps = c.prepareStatement(DbQueries.GET_CONNECTION_CONTEXT);
                var rs = ps.executeQuery()) {

            if (!rs.next())
                return null;

            return DbIdentityDto.builder()
                    .database(rs.getString("db"))
                    .currentUser(rs.getString("curr_user"))
                    .serverAddr(rs.getString("server_addr"))
                    .serverPort(rs.getInt("server_port"))
                    .serverVersion(rs.getString("server_version"))
                    .serverTime(rs.getObject("server_time", OffsetDateTime.class))
                    .schema(schema)
                    .build();
        }
    }

    private boolean flywayHistoryExists(Connection c) throws SQLException {
        try (var ps = c.prepareStatement(DbQueries.GET_FLYWAY_HISTORY);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getString("flyway_table") != null;
        }
    }

    private LatestAppliedDto queryLatestApplied(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(DbQueries.GET_LATEST_APPLIED);
                var rs = ps.executeQuery()) {

            if (!rs.next())
                return null;

            return LatestAppliedDto.builder()
                    .installedRank((Integer) rs.getObject("installed_rank"))
                    .version(rs.getString("version"))
                    .description(rs.getString("description"))
                    .script(rs.getString("script"))
                    .installedOn(getOffsetDateTime(rs, "installed_on"))
                    .installedBy(rs.getString("installed_by"))
                    .build();
        }
    }

    private int queryFlywayFailedCount(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(DbQueries.GET_FLYWAY_FAILED_COUNT);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("cnt");
        }
    }

    /**
     * Prefer OffsetDateTime if supported. If the column is timestamp (no tz), fall
     * back to UTC.
     */
    private OffsetDateTime getOffsetDateTime(ResultSet rs, String col) throws SQLException {
        try {
            var odt = rs.getObject(col, OffsetDateTime.class);
            if (odt != null)
                return odt;
        } catch (SQLException ignored) {
            // driver/column type may not support OffsetDateTime mapping
        }

        var ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant().atOffset(ZoneOffset.UTC) : null;
    }

    private List<WarningMessageDto> computeWarnings(FlywaySummaryDto summary, DbIdentityDto identity) {
        List<WarningMessageDto> warnings = new ArrayList<>();

        // Check for multiple installers
        Set<String> installers = summary.installedBySummary().stream()
                .map(FlywayInstalledBySummaryDto::installedBy)
                .collect(Collectors.toSet());

        if (installers.size() > 1) {
            warnings.add(WarningMessageDto.builder()
                    .code("MULTIPLE_INSTALLERS")
                    .message(String.format("Migrations were applied by %d different users: %s",
                            installers.size(), String.join(", ", installers)))
                    .build());
        }

        // Check for credential drift
        if (summary.latestApplied() != null && identity.currentUser() != null) {
            String latestInstalledBy = summary.latestApplied().installedBy();
            String currentUser = identity.currentUser();

            if (!currentUser.equals(latestInstalledBy)) {
                warnings.add(WarningMessageDto.builder()
                        .code("CREDENTIAL_DRIFT")
                        .message(String.format(
                                "Latest migration was installed by %s, but you are connected as %s",
                                latestInstalledBy, currentUser))
                        .build());
            }
        }

        return warnings;
    }

    private DataSource buildDataSource(DbConnectContextDto ctx) {
        log.info(
                "#buildDataSource: Building data source for connectionId={}, host={}, port={}, db={}, user={}, sslMode={}, schema={}",
                ctx.id(), ctx.host(), ctx.port(), ctx.database(), ctx.username(), ctx.sslMode(), ctx.schema());

        var ds = new PGSimpleDataSource();
        var sslMode = (ctx.sslMode() == null || ctx.sslMode().isBlank()) ? "require" : ctx.sslMode();

        var url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                ctx.host(), ctx.port(), ctx.database(), sslMode);

        ds.setUrl(url);
        ds.setUser(ctx.username());
        ds.setPassword(ctx.password());
        ds.setConnectTimeout(CONNECT_TIMEOUT_SECONDS);
        ds.setLoginTimeout(CONNECT_TIMEOUT_SECONDS);
        return ds;
    }

    private record FlywayEvaluation(FlywayStatus status, String message) {
    }
}
