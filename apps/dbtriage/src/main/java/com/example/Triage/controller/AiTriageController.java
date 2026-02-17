package com.example.Triage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Triage.model.request.AiTriageRequest;
import com.example.Triage.model.response.AiTriageResponse;
import com.example.Triage.service.AiTriageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiTriageController {

    private final AiTriageService aiTriageService;

    @PostMapping("/triage")
    public ResponseEntity<AiTriageResponse> triage(@Valid @RequestBody AiTriageRequest request) {
        log.info("#aiTriage: tool={}, action={}, connectionId={}",
                request.tool(), request.action(), request.connectionId());
        AiTriageResponse response = aiTriageService.triage(request);
        return ResponseEntity.ok(response);
    }
}
