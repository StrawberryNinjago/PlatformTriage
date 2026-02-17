package com.example.platformtriage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.platformtriage.model.request.AiTriageRequest;
import com.example.platformtriage.model.response.AiTriageResponse;
import com.example.platformtriage.service.AiTriageService;

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
        log.info("#platformAiTriage: tool={}, action={}, contextProvided={}",
                request.tool(), request.action(), request.context() != null);
        AiTriageResponse response = aiTriageService.triage(request);
        return ResponseEntity.ok(response);
    }
}
