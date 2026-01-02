package com.example.Triage.handler;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.exception.DbPrivilegesCheckException;
import com.example.Triage.exception.InvalidTableException;
import com.example.Triage.exception.PrivilegesCheckFailedException;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbPrivilegeSummaryDto;
import com.example.Triage.model.response.DbPrivilegesResponse;
import com.example.Triage.service.db.DbConnectionRegistry;
import com.example.Triage.service.db.preivilege.DbPrivilegesService;
import com.example.Triage.service.db.preivilege.DbTablePrivilegesService;
import com.example.Triage.util.LogUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbPrivilegesHandler {

    private final DbConnectionRegistry registry;
    private final DbPrivilegesService privilegesService;
    private final DbTablePrivilegesService tablePrivilegesService;

    public DbPrivilegeSummaryDto getPrivilegesSummary(String connectionId) {
        log.info("#getPrivilegesSummary: connectionId={}", connectionId);

        DbConnectContextDto ctx = registry.get(connectionId)
                .orElseThrow(() -> new ConnectionNotFoundException("CONNECTION_NOT_FOUND"));

        try {
            return privilegesService.getPrivileges(ctx);
        } catch (SQLException e) {
            log.error("#getPrivilegesSummary: SQL failure connectionId={}", connectionId, e);
            throw new DbPrivilegesCheckException("DB_PRIVILEGES_SUMMARY_SQL_FAILED", e,
                    Map.of("connectionId", connectionId));
        } catch (Exception e) {
            log.error("#getPrivilegesSummary: Unexpected failure connectionId={}", connectionId, e);
            throw new DbPrivilegesCheckException(
                    "DB_PRIVILEGES_SUMMARY_FAILED", e, Map.of("connectionId", connectionId));
        }
    }

    public DbPrivilegesResponse checkTablePrivileges(String connectionId, String schema, String table) {
        log.info("#checkTablePrivileges: connectionId={}, schema={}, table={}", connectionId, schema, table);

        DbConnectContextDto ctx = registry.get(connectionId)
                .orElseThrow(() -> new ConnectionNotFoundException("CONNECTION_NOT_FOUND"));

        if (table == null || table.isBlank()) {
            throw new InvalidTableException("Table name is required.");
        }

        String resolvedSchema = (schema == null || schema.isBlank()) ? "public" : schema;

        try {
            return tablePrivilegesService.checkTablePrivileges(ctx, resolvedSchema, table);
        } catch (SQLException e) {
            log.error("#checkTablePrivileges: SQL failure connectionId={}, schema={}, table={}",
                    connectionId, resolvedSchema, table, e);
            throw new PrivilegesCheckFailedException("DB_TABLE_PRIVILEGES_SQL_FAILED", e);
        } catch (Exception e) {
            log.error("#checkTablePrivileges: Unexpected failure connectionId={}, schema={}, table={}",
                    connectionId, resolvedSchema, table, e);
            throw new PrivilegesCheckFailedException(LogUtils.safeMessage(e), e);
        }
    }
}
