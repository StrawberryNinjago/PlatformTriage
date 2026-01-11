package com.example.platformtriage.detection;

import java.util.Map;

/**
 * Normalized view of a service.
 */
public record ServiceView(
    String name,
    String type,              // ClusterIP, NodePort, LoadBalancer
    Map<String, String> selector
) {}
