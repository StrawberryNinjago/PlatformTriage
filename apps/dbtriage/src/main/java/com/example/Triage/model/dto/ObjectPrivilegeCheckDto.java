package com.example.Triage.model.dto;

import lombok.Builder;

@Builder
public record ObjectPrivilegeCheckDto(
        String objectName, // e.g. public.cart
        String objectType, // TABLE / SCHEMA
        String privilege, // SELECT / INSERT / USAGE / CREATE
        boolean allowed) {
}
