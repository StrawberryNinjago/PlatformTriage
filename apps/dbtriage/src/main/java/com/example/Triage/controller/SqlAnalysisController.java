package com.example.Triage.controller;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.handler.SqlAnalysisHandler;
import com.example.Triage.model.request.SqlAnalysisRequest;
import com.example.Triage.model.response.ErrorResponse;
import com.example.Triage.util.LogUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/analyze")
@RequiredArgsConstructor
@Slf4j
public class SqlAnalysisController {
    
    private final SqlAnalysisHandler sqlAnalysisHandler;

    @PostMapping
    public ResponseEntity<?> analyzeSql(@Valid @RequestBody SqlAnalysisRequest request) {
        log.info("#analyzeSql: Received SQL analysis request for connectionId: {}", request.connectionId());
        
        try {
            var resp = sqlAnalysisHandler.analyzeSql(request);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("#analyzeSql: SQL analysis failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("SQL_ANALYSIS_FAILED", LogUtils.safeMessage(e)));
        }
    }
}

