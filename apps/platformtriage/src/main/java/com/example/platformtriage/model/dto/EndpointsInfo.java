package com.example.platformtriage.model.dto;

public record EndpointsInfo(
    String serviceName,
    int readyAddresses,
    int notReadyAddresses
) {}
