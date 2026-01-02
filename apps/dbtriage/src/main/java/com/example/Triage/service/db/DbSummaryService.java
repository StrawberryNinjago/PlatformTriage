package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbIdentityDto;
import com.example.Triage.model.dto.FlywaySummaryDto;
import com.example.Triage.model.dto.LatestAppliedDto;
import com.example.Triage.model.dto.SchemaSummaryDto;
import com.example.Triage.model.dto.TableExistence;
import com.example.Triage.model.response.DbSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbSummaryService {

    private final DbDataSourceFactory dataSourceFactory;

    private static final List<String> IMPORTANT_TABLES = List.of(
            "cart",
            "line_of_service",
            "cart_item",
            "order_item",
            "shipping_info");

    public DbSummaryResponse getSummary(DbConnectContextDto ctx) throws SQLException {
        log.info("#getSummary: Getting summary for context: {}", ctx);
        var ds = dataSourceFactory.build(ctx);

        try (var c = ds.getConnection()) {
            var identity = queryIdentity(c, ctx.schema());
            var flywayExists = flywayHistoryExists(c);

            var flyway = FlywaySummaryDto.builder()
                    .historyTableExists(flywayExists)
                    .latestApplied(flywayExists ? queryLatestApplied(c) : null)
                    .failedCount(flywayExists ? queryFlywayFailedCount(c) : 0)
                    .build();

            var publicTableCount = queryPublicTableCount(c);
            var important = queryImportantTablesExistence(c);
            var schema = SchemaSummaryDto.builder()
                    .tableCount(publicTableCount)
                    .importantTables(important)
                    .build();
            log.info("#getSummary: Summary: {}", schema);
            return new DbSummaryResponse(identity, flyway, schema);
        }
    }

    private DbIdentityDto queryIdentity(Connection c, String schema) throws SQLException {
        log.info("#queryIdentity: Querying identity for connection: {}", c);
        try (var ps = c.prepareStatement(DbQueries.GET_IDENTITY);
                var rs = ps.executeQuery()) {
            rs.next();
            var db = rs.getString("db");
            var usr = rs.getString("usr");
            var addr = rs.getString("server_addr");
            int port = rs.getInt("server_port");
            var ver = rs.getString("server_version");

            var ts = rs.getTimestamp("server_time");
            var serverTime = ts == null
                    ? null
                    : OffsetDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault());
            var identity = DbIdentityDto.builder()
                    .database(db)
                    .currentUser(usr)
                    .serverAddr(addr)
                    .serverPort(port)
                    .serverVersion(ver)
                    .serverTime(serverTime)
                    .schema(schema)
                    .build();
            log.info("#queryIdentity: Identity: {}", identity);
            return identity;
        }
    }

    private boolean flywayHistoryExists(Connection c) throws SQLException {
        try (var ps = c.prepareStatement(DbQueries.GET_FLYWAY_HISTORY);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getString("flyway_table") != null;
        }
    }

    private LatestAppliedDto queryLatestApplied(Connection c) throws SQLException {
        log.info("#queryLatestApplied: Querying latest applied for connection: {}", c);
        try (var ps = c.prepareStatement(DbQueries.GET_LATEST_APPLIED);
                var rs = ps.executeQuery()) {
            if (!rs.next()) {
                log.info("#queryLatestApplied: No latest applied found");
                return null;
            }

            var rank = (Integer) rs.getObject("installed_rank");
            var version = rs.getString("version");
            var description = rs.getString("description");
            var script = rs.getString("script");
            log.debug("#queryLatestApplied: Version: {}, Description: {}, Script: {}", version, description, script);
            var ts = rs.getTimestamp("installed_on");
            var installedOn = ts == null
                    ? null
                    : OffsetDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault());
            var installedBy = rs.getString("installed_by");

            var latestApplied = LatestAppliedDto.builder()
                    .installedRank(rank)
                    .version(version)
                    .description(description)
                    .script(script)
                    .installedOn(installedOn)
                    .installedBy(installedBy)
                    .build();
            log.info("#queryLatestApplied: Latest applied: {}", latestApplied);
            return latestApplied;
        }
    }

    private int queryFlywayFailedCount(Connection c) throws SQLException {
        log.info("#queryFlywayFailedCount: Querying flyway failed count for connection: {}", c);
        try (var ps = c.prepareStatement(DbQueries.GET_FLYWAY_FAILED_COUNT);
                var rs = ps.executeQuery()) {
            rs.next();

            var failedCount = rs.getInt("cnt");
            log.info("#queryFlywayFailedCount: Flyway failed count: {}", failedCount);
            return failedCount;
        }
    }

    private int queryPublicTableCount(Connection c) throws SQLException {
        log.info("#queryPublicTableCount: Querying public table count for connection: {}", c);
        try (var ps = c.prepareStatement(DbQueries.GET_PUBLIC_TABLE_COUNT);
                var rs = ps.executeQuery()) {
            rs.next();
            var tableCount = rs.getInt("cnt");
            log.info("#queryPublicTableCount: Public table count: {}", tableCount);
            return tableCount;
        } catch (SQLException e) {
            log.error("#queryPublicTableCount: Error querying public table count: {}", e.getMessage());
            throw e;
        }
    }

    private List<TableExistence> queryImportantTablesExistence(Connection c)
            throws SQLException {
        log.info("#queryImportantTablesExistence: Querying important tables existence for connection: {}", c);
        List<TableExistence> out = new ArrayList<>();
        try (var ps = c.prepareStatement(DbQueries.IS_TABLE_EXIST)) {
            for (var t : IMPORTANT_TABLES) {
                ps.setString(1, t);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    out.add(new TableExistence(t, rs.getBoolean("present")));
                }
            }
        }
        log.info("#queryImportantTablesExistence: Important tables existence: {}", out);
        return out;
    }
}
