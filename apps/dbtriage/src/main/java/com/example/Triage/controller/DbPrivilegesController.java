package com.example.Triage.controller;

import com.example.Triage.model.dto.DbPrivilegeSummaryDto;
import com.example.Triage.model.response.DbPrivilegesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.Triage.handler.DbPrivilegesHandler;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/connections")
public class DbPrivilegesController {

    private final DbPrivilegesHandler privilegesHandler;

    @GetMapping("/{id}/privileges/summary")
    public DbPrivilegeSummaryDto getPrivilegesSummary(@PathVariable String id) {
        log.info("#getPrivilegesSummary: connectionId={}", id);
        return privilegesHandler.getPrivilegesSummary(id);
    }

    @GetMapping("/{id}/table-privileges")
    public DbPrivilegesResponse checkTablePrivileges(
            @PathVariable String id,
            @RequestParam(defaultValue = "public") String schema,
            @RequestParam String table) {
        log.info("#checkTablePrivileges: connectionId={}, schema={}, table={}", id, schema, table);
        return privilegesHandler.checkTablePrivileges(id, schema, table);
    }
}
