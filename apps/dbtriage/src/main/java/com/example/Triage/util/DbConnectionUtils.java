package com.example.Triage.util;

import com.example.Triage.model.dto.DbConnectContext;
import com.example.Triage.model.errorhandling.ConnectionNotFoundException;
import com.example.Triage.service.db.DbConnectionRegistry;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class DbConnectionUtils {

    public static DbConnectContext getCtx(DbConnectionRegistry registry, String connectionId) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            throw new ConnectionNotFoundException("Connection not found or expired. Please connect again.");
        }
        return ctx;
    }
}
