package com.example.Triage.handler;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.model.request.SqlAnalysisRequest;
import com.example.Triage.model.response.SqlAnalysisResponse;
import com.example.Triage.service.db.DbConnectionRegistry;
import com.example.Triage.service.db.SqlAnalysisService;
import com.example.Triage.util.DbConnectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SqlAnalysisHandler {

    private final DbConnectionRegistry registry;
    private final SqlAnalysisService sqlAnalysisService;

    public SqlAnalysisResponse analyzeSql(SqlAnalysisRequest request) throws Exception {
        log.info("#analyzeSql: Analyzing SQL for connectionId: {}, operation: {}", 
                request.connectionId(), request.operationType());
        
        var ctx = DbConnectionUtils.getCtx(registry, request.connectionId());

        try {
            var resp = sqlAnalysisService.analyzeSql(ctx, request.sql(), request.operationType());
            log.info("#analyzeSql: SQL analyzed successfully for connectionId: {}", request.connectionId());
            return resp;
        } catch (Exception e) {
            log.error("#analyzeSql: SQL analysis failed for connectionId: {}", request.connectionId(), e);
            throw new ConnectionNotFoundException("SQL_ANALYSIS_FAILED: " + e.getMessage());
        }
    }
}

