package com.example.Triage.controller;

import com.example.Triage.core.DbConnectionRegistry;
import com.example.Triage.core.DbConnectionService;
import com.example.Triage.core.DbSummaryService;
import com.example.Triage.core.DbIdentityService;
import com.example.Triage.core.DbFlywayService;
import com.example.Triage.model.request.DbConnectionRequest;
import com.example.Triage.model.response.DbConnectionResponse;
import com.example.Triage.model.response.DbSummaryResponse;
import com.example.Triage.model.response.DbIdentityResponse;
import com.example.Triage.model.response.DbFlywayHealthResponse;
import com.example.Triage.model.errorhandling.ApiError;
import com.example.Triage.model.response.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/db")
public class DbConnectionController {

    private final DbConnectionRegistry registry;
    private final DbConnectionService service;
    private final DbSummaryService summaryService;
    private final DbIdentityService identityService;
    private final DbFlywayService flywayService;

    public DbConnectionController(DbConnectionRegistry registry, DbConnectionService service,
            DbSummaryService summaryService, DbIdentityService identityService,
            DbFlywayService flywayService) {
        this.registry = registry;
        this.service = service;
        this.summaryService = summaryService;
        this.identityService = identityService;
        this.flywayService = flywayService;
    }

    @PostMapping("/connections")
    public ResponseEntity<?> createConnection(@Valid @RequestBody DbConnectionRequest req) {
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
            return ResponseEntity.ok(new DbConnectionResponse(ctx.id(), true));
        } catch (Exception ex) {
            // Do not leak passwords/connection strings. Provide a clean message.
            registry.delete(ctx.id());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("DB_CONNECT_FAILED", safeMessage(ex)));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam String connectionId) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        try {
            var resp = summaryService.getSummary(ctx);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("DB_SUMMARY_FAILED", safeMessage(e)));
        }
    }

    @GetMapping("/identity")
    public ResponseEntity<?> getIdentity(@RequestParam String connectionId) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        try {
            DbIdentityResponse resp = identityService.getIdentity(ctx);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("DB_IDENTITY_FAILED", safeMessage(e)));
        }
    }

    @GetMapping("/flyway/health")
    public ResponseEntity<?> getFlywayHealth(@RequestParam String connectionId) {
        var ctx = registry.get(connectionId).orElse(null);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND",
                            "Connection not found or expired. Please connect again."));
        }

        try {
            DbFlywayHealthResponse resp = flywayService.getFlywayHealth(ctx);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("FLYWAY_HEALTH_FAILED", safeMessage(e)));
        }
    }

    private String safeMessage(Exception ex) {
        // Keep it user-friendly and not too verbose for MVP.
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank())
            return "Failed to connect to database.";
        // add more redaction here if needed.
        return msg;
    }
}
