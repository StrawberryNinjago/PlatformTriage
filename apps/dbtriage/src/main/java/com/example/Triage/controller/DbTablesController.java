package com.example.Triage.controller;

import com.example.Triage.core.DbConnectionRegistry;
import com.example.Triage.core.DbTablesService;
import com.example.Triage.core.DbSummaryService;
import com.example.Triage.core.DbIntrospectService;
import com.example.Triage.model.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/db/tables")
public class DbTablesController {

    private final DbConnectionRegistry registry;
    private final DbTablesService tablesService;
    private final DbSummaryService summaryService;
    private final DbIntrospectService introspectService;

    public DbTablesController(DbConnectionRegistry registry, DbTablesService tablesService,
                              DbSummaryService summaryService, DbIntrospectService introspectService) {
        this.registry = registry;
        this.tablesService = tablesService;
        this.summaryService = summaryService;
        this.introspectService = introspectService;
    }

    @GetMapping
    public ResponseEntity<?> listTables(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        try {
            return ResponseEntity.ok(tablesService.listTables(ctx, schema));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("LIST_TABLES_FAILED", safeMessage(e)));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTables(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String q) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        if (q == null || q.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("INVALID_QUERY", "Search query 'q' cannot be empty."));
        }

        try {
            return ResponseEntity.ok(tablesService.searchTables(ctx, schema, q));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("SEARCH_TABLES_FAILED", safeMessage(e)));
        }
    }

    @GetMapping("/indexes")
    public ResponseEntity<?> listIndexes(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String table) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        try {
            return ResponseEntity.ok(summaryService.listIndexes(ctx, schema, table));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("LIST_INDEXES_FAILED", safeMessage(e)));
        }
    }

    @GetMapping("/introspect")
    public ResponseEntity<?> introspectTable(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String table) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        try {
            return ResponseEntity.ok(introspectService.introspectTable(ctx, schema, table));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("TABLE_INTROSPECT_FAILED", safeMessage(e)));
        }
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? "Operation failed." : msg;
    }
}

