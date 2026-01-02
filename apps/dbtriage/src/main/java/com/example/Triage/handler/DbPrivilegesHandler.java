package com.example.Triage.handler;

import org.springframework.stereotype.Component;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.exception.InvalidTableException;
import com.example.Triage.exception.PrivilegesCheckFailedException;
import com.example.Triage.model.response.DbPrivilegesResponse;
import com.example.Triage.service.db.DbConnectionRegistry;
import com.example.Triage.service.db.DbPrivilegesService;
import com.example.Triage.util.LogUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbPrivilegesHandler {

    private final DbConnectionRegistry registry;
    private final DbPrivilegesService privilegesService;

    public DbPrivilegesResponse checkPrivileges(String connectionId, String schema, String table) throws Exception {
        log.info("#checkPrivileges: Checking privileges for connectionId: {}, schema: {}, table: {}", connectionId,
                schema, table);
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            log.error("#checkPrivileges: Connection not found or expired for connectionId: {}", connectionId);
            throw new ConnectionNotFoundException("Connection not found or expired. Please connect again.");
        }

        if (table == null || table.isBlank()) {
            log.error("#checkPrivileges: Table name is required for connectionId: {}", connectionId);
            throw new InvalidTableException("Table name is required.");
        }

        try {
            var resp = privilegesService.checkPrivileges(ctx, schema, table);
            log.info("#checkPrivileges: Privileges checked successfully for connectionId: {}, schema: {}, table: {}",
                    connectionId, schema, table);
            return resp;
        } catch (Exception e) {
            log.error("#checkPrivileges: Privileges check failed for connectionId: {}, schema: {}, table: {}",
                    connectionId, schema, table, e);
            throw new PrivilegesCheckFailedException(LogUtils.safeMessage(e));
        }
    }
}
