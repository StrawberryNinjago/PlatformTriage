package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContext;
import com.example.Triage.model.dto.DbIdentity;
import com.example.Triage.model.dto.FlywaySummary;
import com.example.Triage.model.dto.LatestApplied;
import com.example.Triage.model.dto.SchemaSummary;
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

    public DbSummaryResponse getSummary(DbConnectContext ctx) throws SQLException {
        log.info("#getSummary: Getting summary for context: {}", ctx);
        var ds = dataSourceFactory.build(ctx);

        try (var c = ds.getConnection()) {
            var identity = queryIdentity(c);
            var flywayExists = flywayHistoryExists(c);

            var flyway = new FlywaySummary(
                    flywayExists,
                    flywayExists ? queryLatestApplied(c) : null,
                    flywayExists ? queryFlywayFailedCount(c) : 0);

            var publicTableCount = queryPublicTableCount(c);
            var important = queryImportantTablesExistence(c);
            var schema = new SchemaSummary(
                    publicTableCount,
                    important);
            log.info("#getSummary: Summary: {}", schema);
            return new DbSummaryResponse(identity, flyway, schema);
        }
    }

    private DbIdentity queryIdentity(Connection c) throws SQLException {
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
            log.info("#queryIdentity: Identity: {}", new DbIdentity(db, usr, addr, port, ver, serverTime));
            return new DbIdentity(db, usr, addr, port, ver, serverTime);
        }
    }

    private boolean flywayHistoryExists(Connection c) throws SQLException {
        try (var ps = c.prepareStatement(DbQueries.GET_FLYWAY_HISTORY);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getString("flyway_table") != null;
        }
    }

    private LatestApplied queryLatestApplied(Connection c) throws SQLException {
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
            log.info("#queryLatestApplied: Latest applied: {}",
                    new LatestApplied(rank, version, description, script, installedOn));
            return new LatestApplied(rank, version, description, script, installedOn);
        }
    }

    private int queryFlywayFailedCount(Connection c) throws SQLException {
        log.info("#queryFlywayFailedCount: Querying flyway failed count for connection: {}", c);
        try (var ps = c.prepareStatement(DbQueries.GET_FLYWAY_FAILED_COUNT);
                var rs = ps.executeQuery()) {
            rs.next();
            log.info("#queryFlywayFailedCount: Flyway failed count: {}", rs.getInt("cnt"));
            return rs.getInt("cnt");
        }
    }

    private int queryPublicTableCount(Connection c) throws SQLException {
        log.info("#queryPublicTableCount: Querying public table count for connection: {}", c);
        try (var ps = c.prepareStatement(DbQueries.GET_PUBLIC_TABLE_COUNT);
                var rs = ps.executeQuery()) {
            rs.next();
            log.info("#queryPublicTableCount: Public table count: {}", rs.getInt("cnt"));
            return rs.getInt("cnt");
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
