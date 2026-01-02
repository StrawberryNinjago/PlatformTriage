package com.example.Triage.handler;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

import com.example.Triage.exception.ApiError;
import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.exception.DbPrivilegesCheckException;
import com.example.Triage.exception.InvalidTableException;
import com.example.Triage.exception.PrivilegesCheckFailedException;
import com.example.Triage.model.dto.DiagnosticItem;
import com.example.Triage.model.dto.NextAction;
import com.example.Triage.model.enums.Severity;
import com.example.Triage.model.response.ApiErrorResponse;
import com.example.Triage.util.LogUtils;

@RestControllerAdvice
public class DbDoctorExceptionHandler {

        @ExceptionHandler(ConnectionNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleConnectionNotFound(
                        ConnectionNotFoundException e,
                        HttpServletRequest req) {
                return build(
                                HttpStatus.NOT_FOUND,
                                "CONNECTION_NOT_FOUND",
                                "Connection not found",
                                e.getMessage(),
                                Severity.WARN,
                                req,
                                Map.of("feature", "CONNECTIONS"),
                                List.of("Reconnect and try again."),
                                List.of(new NextAction("Re-connect", "CONNECTIONS")),
                                false,
                                List.of());
        }

        @ExceptionHandler(InvalidTableException.class)
        public ResponseEntity<ApiErrorResponse> handleInvalidTable(
                        InvalidTableException e,
                        HttpServletRequest req) {
                return build(
                                HttpStatus.BAD_REQUEST,
                                "INVALID_TABLE",
                                "Invalid table",
                                e.getMessage(),
                                Severity.WARN,
                                req,
                                Map.of("feature", "PRIVILEGES"),
                                List.of("Provide a non-empty table name."),
                                List.of(new NextAction("Open Privileges Summary", "PRIVILEGES_SUMMARY")),
                                false,
                                List.of());
        }

        @ExceptionHandler(DbPrivilegesCheckException.class)
        public ResponseEntity<ApiErrorResponse> handlePrivilegesCheck(
                        DbPrivilegesCheckException e,
                        HttpServletRequest req) {
                List<DiagnosticItem> diag = new ArrayList<>();
                Throwable cause = e.getCause();
                if (cause instanceof SQLException se && se.getSQLState() != null) {
                        diag.add(new DiagnosticItem("sqlState", se.getSQLState()));
                }

                // If you later change DbPrivilegesCheckException to have e.code(), use that.
                String code = (e.getMessage() != null && !e.getMessage().isBlank())
                                ? e.getMessage()
                                : "DB_PRIVILEGES_CHECK_FAILED";

                return build(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                code,
                                "Database privileges check failed",
                                LogUtils.safeMessage(e),
                                Severity.ERROR,
                                req,
                                Map.of("feature", "PRIVILEGES"),
                                List.of(
                                                "Verify you connected using the expected credential.",
                                                "Run Privileges Summary to confirm schema/table access."),
                                List.of(new NextAction("Open Privileges Summary", "PRIVILEGES_SUMMARY")),
                                false,
                                diag);
        }

        @ExceptionHandler(PrivilegesCheckFailedException.class)
        public ResponseEntity<ApiErrorResponse> handleTablePrivilegesCheckFailed(
                        PrivilegesCheckFailedException e,
                        HttpServletRequest req) {
                return build(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "DB_TABLE_PRIVILEGES_CHECK_FAILED",
                                "Table privileges check failed",
                                LogUtils.safeMessage(e),
                                Severity.ERROR,
                                req,
                                Map.of("feature", "PRIVILEGES"),
                                List.of("Verify schema/table name and connected credential."),
                                List.of(new NextAction("Open Privileges Summary", "PRIVILEGES_SUMMARY")),
                                false,
                                List.of());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleUnknown(
                        Exception e,
                        HttpServletRequest req) {
                return build(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "UNKNOWN_ERROR",
                                "Unexpected error",
                                LogUtils.safeMessage(e),
                                Severity.ERROR,
                                req,
                                Map.of("feature", "UNKNOWN"),
                                List.of("Retry. If it persists, share the correlationId with the team."),
                                List.of(),
                                false,
                                List.of());
        }

        private ResponseEntity<ApiErrorResponse> build(
                        HttpStatus httpStatus,
                        String code,
                        String title,
                        String message,
                        Severity severity,
                        HttpServletRequest req,
                        Map<String, Object> context,
                        List<String> hints,
                        List<NextAction> nextActions,
                        boolean retryable,
                        List<DiagnosticItem> diagnostics) {
                String correlationId = getOrCreateCorrelationId(req);
                OffsetDateTime timestamp = OffsetDateTime.now();

                ApiError apiError = ApiError.builder()
                                .code(code)
                                .title(title)
                                .message(message)
                                .severity(severity)
                                .httpStatus(httpStatus.value())
                                .timestamp(timestamp)
                                .correlationId(correlationId)
                                .context(context)
                                .diagnostics(diagnostics)
                                .hints(hints)
                                .nextActions(nextActions)
                                .retryable(retryable)
                                .build();

                return ResponseEntity.status(httpStatus)
                                .body(ApiErrorResponse.builder().error(apiError).build());
        }

        private String getOrCreateCorrelationId(HttpServletRequest req) {
                String id = req.getHeader("X-Correlation-Id");
                if (id == null || id.isBlank()) {
                        id = java.util.UUID.randomUUID().toString();
                }
                return id;
        }
}
