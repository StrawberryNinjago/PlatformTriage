package com.example.Triage.controller;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.handler.EnvironmentComparisonHandler;
import com.example.Triage.model.request.EnvironmentComparisonRequest;
import com.example.Triage.model.response.EnvironmentComparisonResponse;
import com.example.Triage.model.response.ErrorResponse;
import com.example.Triage.util.LogUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for environment comparison (schema drift detection)
 */
@RestController
@RequestMapping("/api/db/environments")
@RequiredArgsConstructor
@Slf4j
public class EnvironmentComparisonController {

    private final EnvironmentComparisonHandler comparisonHandler;

    @PostMapping("/compare")
    public ResponseEntity<?> compareEnvironments(@Valid @RequestBody EnvironmentComparisonRequest request) {
        log.info("#compareEnvironments: Comparing {} -> {}",
                request.sourceEnvironmentName(), request.targetEnvironmentName());
        
        try {
            EnvironmentComparisonResponse response = comparisonHandler.compareEnvironments(request);
            return ResponseEntity.ok(response);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("CONNECTION_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Environment comparison failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("COMPARISON_FAILED", LogUtils.safeMessage(e)));
        }
    }
}

