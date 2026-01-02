package com.example.Triage.controller;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.handler.DbConnectionHandler;
import com.example.Triage.model.request.DbConnectionRequest;
import com.example.Triage.model.response.ErrorResponse;
import com.example.Triage.util.LogUtils;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
@Slf4j
public class DbConnectionController {

    private final DbConnectionHandler connectionHandler;

    @PostMapping("/connections")
    public ResponseEntity<?> createConnection(@Valid @RequestBody DbConnectionRequest req) {
        log.info("#createConnection: Creating connection for request: {}", req);
        try {
            var resp = connectionHandler.createConnection(req);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("DB_CONNECT_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam String connectionId) {
        log.info("#summary: Getting summary for connectionId: {}", connectionId);
        try {
            var resp = connectionHandler.getSummary(connectionId);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("DB_SUMMARY_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/identity")
    public ResponseEntity<?> getIdentity(@RequestParam String connectionId) {
        log.info("#getIdentity: Getting identity for connectionId: {}", connectionId);
        try {
            var resp = connectionHandler.getIdentity(connectionId);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("DB_IDENTITY_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/flyway/health")
    public ResponseEntity<?> getFlywayHealth(@RequestParam String connectionId) {
        log.info("#getFlywayHealth: Getting flyway health for connectionId: {}", connectionId);
        try {
            var resp = connectionHandler.getFlywayHealth(connectionId);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("FLYWAY_HEALTH_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/connections/{connectionId}/flyway/history")
    public ResponseEntity<?> getFlywayHistory(@RequestParam String connectionId, @RequestParam int limit) {
        log.info("#getFlywayHistory: Getting flyway history for connectionId: {}", connectionId);
        try {
            var resp = connectionHandler.getFlywayHistory(connectionId, limit);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("FLYWAY_HISTORY_FAILED", LogUtils.safeMessage(e)));
        }
    }
}
