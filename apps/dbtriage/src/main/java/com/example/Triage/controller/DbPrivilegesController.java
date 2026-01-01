package com.example.Triage.controller;

import com.example.Triage.model.enums.DbSchema;
import com.example.Triage.model.errorhandling.ConnectionNotFoundException;
import com.example.Triage.model.errorhandling.InvalidTableException;
import com.example.Triage.model.errorhandling.PrivilegesCheckFailedException;
import com.example.Triage.model.request.DbPrivilegesRequest;
import com.example.Triage.model.response.ErrorResponse;
import com.example.Triage.util.LogUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Triage.handler.DbPrivilegesHandler;

@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
@Slf4j
public class DbPrivilegesController {
    private final DbPrivilegesHandler privilegesHandler;

    @PostMapping("/privileges:check")
    public ResponseEntity<?> checkPrivileges(@RequestBody DbPrivilegesRequest req) {

        String resolvedSchema = req.schema() == null || req.schema() == DbSchema.PUBLIC
                ? "public"
                : req.schemaName();

        log.info("#checkPrivileges: connectionId={}, schema={}, table={}",
                req.connectionId(), resolvedSchema, req.tableName());

        try {
            return ResponseEntity.ok(privilegesHandler.checkPrivileges(req.connectionId(),
                    resolvedSchema, req.tableName()));
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (InvalidTableException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("INVALID_TABLE", e.getMessage()));
        } catch (PrivilegesCheckFailedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("PRIVILEGES_CHECK_FAILED", LogUtils.safeMessage(e)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("UNKNOWN_ERROR", LogUtils.safeMessage(e)));
        }
    }
}
