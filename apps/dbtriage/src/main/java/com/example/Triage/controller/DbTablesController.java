package com.example.Triage.controller;

import com.example.Triage.handler.DbTablesHandler;
import com.example.Triage.model.errorhandling.ConnectionNotFoundException;
import com.example.Triage.model.response.ErrorResponse;
import com.example.Triage.util.LogUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/db/tables")
@RequiredArgsConstructor
@Slf4j
public class DbTablesController {
    private final DbTablesHandler tablesHandler;

    @GetMapping
    public ResponseEntity<?> listTables(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema) {
        log.info("#listTables: Listing tables for connectionId: {}, schema: {}", connectionId, schema);
        try {
            var resp = tablesHandler.listTables(connectionId, schema);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("LIST_TABLES_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTables(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String queryString) {
        log.info("#searchTables: Searching tables for connectionId: {}, schema: {}, queryString: {}", connectionId,
                schema,
                queryString);
        if (queryString == null || queryString.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("INVALID_QUERY", "Search query 'q' cannot be empty."));
        }

        try {
            var resp = tablesHandler.searchTables(connectionId, schema, queryString);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("SEARCH_TABLES_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/indexes")
    public ResponseEntity<?> listIndexes(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String table) {
        log.info("#listIndexes: Listing indexes for connectionId: {}, schema: {}, table: {}", connectionId, schema,
                table);
        try {
            var resp = tablesHandler.listIndexes(connectionId, schema, table);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("LIST_INDEXES_FAILED", LogUtils.safeMessage(e)));
        }
    }

    @GetMapping("/introspect")
    public ResponseEntity<?> introspectTable(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String table) {
        log.info("#introspectTable: Introspecting table for connectionId: {}, schema: {}, table: {}", connectionId,
                schema, table);
        try {
            var resp = tablesHandler.introspectTable(connectionId, schema, table);
            return ResponseEntity.ok(resp);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("TABLE_INTROSPECT_FAILED", LogUtils.safeMessage(e)));
        }
    }
}
