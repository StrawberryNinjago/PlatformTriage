package com.example.Triage.handler;

import org.springframework.stereotype.Component;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.model.request.DbConnectionRequest;
import com.example.Triage.model.response.DbConnectionResponse;
import com.example.Triage.model.response.DbFlywayHealthResponse;
import com.example.Triage.model.response.DbIdentityResponse;
import com.example.Triage.model.response.DbSummaryResponse;
import com.example.Triage.service.db.DbConnectionRegistry;
import com.example.Triage.service.db.DbConnectionService;
import com.example.Triage.service.db.DbFlywayService;
import com.example.Triage.service.db.DbIdentityService;
import com.example.Triage.service.db.DbSummaryService;
import com.example.Triage.util.DbConnectionUtils;
import com.example.Triage.model.dto.FlywayHistoryRowDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbConnectionHandler {
    private final DbConnectionService service;
    private final DbSummaryService summaryService;
    private final DbConnectionRegistry registry;
    private final DbIdentityService identityService;
    private final DbFlywayService flywayService;

    public DbConnectionResponse createConnection(DbConnectionRequest req) throws Exception {
        log.info("#createConnection: Creating connection for request: {}", req);
        var ctx = registry.create(
                req.host(),
                req.port(),
                req.database(),
                req.username(),
                req.password(),
                req.sslMode(),
                req.schema());

        try {
            service.testConnection(ctx);
            log.info("#createConnection: Connection created successfully for connectionId: {}", ctx.id());
            return new DbConnectionResponse(ctx.id(), true);
        } catch (Exception ex) {
            registry.delete(ctx.id());
            log.error("#createConnection: Connection creation failed for connectionId: {}", ctx.id(), ex);
            throw new ConnectionNotFoundException("Connection not found or expired. Please connect again.");
        }
    }

    public DbSummaryResponse getSummary(String connectionId) throws Exception {
        log.info("#getSummary: Getting summary for connectionId: {}", connectionId);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);
        try {
            var resp = summaryService.getSummary(ctx);
            log.info("#getSummary: Summary retrieved successfully for connectionId: {}", connectionId);
            return resp;
        } catch (Exception e) {
            log.error("#getSummary: Summary retrieval failed for connectionId: {}", connectionId, e);
            throw new ConnectionNotFoundException("DB_SUMMARY_FAILED.");
        }
    }

    public DbIdentityResponse getIdentity(String connectionId) {
        log.info("#getIdentity: Getting identity for connectionId: {}", connectionId);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);
        try {
            var resp = identityService.getIdentity(ctx);
            log.info("#getIdentity: Identity retrieved successfully for connectionId: {}", connectionId);
            return resp;
        } catch (Exception e) {
            log.error("#getIdentity: Identity retrieval failed for connectionId: {}", connectionId, e);
            throw new ConnectionNotFoundException("DB_IDENTITY_FAILED.");
        }
    }

    public DbFlywayHealthResponse getFlywayHealth(String connectionId) {
        log.info("#getFlywayHealth: Getting flyway health for connectionId: {}", connectionId);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);
        try {
            var resp = flywayService.getFlywayHealth(ctx);
            log.info("#getFlywayHealth: Flyway health retrieved successfully for connectionId: {}", connectionId);
            return resp;
        } catch (Exception e) {
            log.error("#getFlywayHealth: Flyway health retrieval failed for connectionId: {}", connectionId, e);
            throw new ConnectionNotFoundException("FLYWAY_HEALTH_FAILED.");
        }
    }

    public List<FlywayHistoryRowDto> getFlywayHistory(String connectionId, int limit) {
        log.info("#getFlywayHistory: Getting flyway history for connectionId: {}", connectionId);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);
        try {
            var resp = flywayService.getFlywayHistory(ctx, limit);
            log.info("#getFlywayHistory: Flyway history retrieved successfully for connectionId: {}", connectionId);
            return resp;
        } catch (Exception e) {
            log.error("#getFlywayHistory: Flyway history retrieval failed for connectionId: {}", connectionId, e);
            throw new ConnectionNotFoundException("FLYWAY_HISTORY_FAILED.");
        }
    }
}
