package com.example.Triage.controller;

import com.example.Triage.core.DbConnectionRegistry;
import com.example.Triage.core.DbPrivilegesService;
import com.example.Triage.model.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/db")
public class DbPrivilegesController {

    private final DbConnectionRegistry registry;
    private final DbPrivilegesService privilegesService;

    public DbPrivilegesController(DbConnectionRegistry registry, DbPrivilegesService privilegesService) {
        this.registry = registry;
        this.privilegesService = privilegesService;
    }

    @GetMapping("/privileges")
    public ResponseEntity<?> checkPrivileges(
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String table) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        if (table == null || table.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("INVALID_TABLE", "Table name is required."));
        }

        try {
            return ResponseEntity.ok(privilegesService.checkPrivileges(ctx, schema, table));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("PRIVILEGES_CHECK_FAILED", safeMessage(e)));
        }
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? "Operation failed." : msg;
    }
}

