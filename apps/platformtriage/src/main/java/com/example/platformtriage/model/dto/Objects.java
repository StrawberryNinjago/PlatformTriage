package com.example.platformtriage.model.dto;

import java.util.List;

public record Objects(
    List<Workload> deployments,
    List<PodInfo> pods,
    List<EventInfo> events,
    List<ServiceInfo> services,
    List<EndpointsInfo> endpoints
) {}