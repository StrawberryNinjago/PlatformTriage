package com.example.platformtriage.model.dto;

import com.example.platformtriage.model.dto.Workload;
import com.example.platformtriage.model.dto.PodInfo;
import com.example.platformtriage.model.dto.EventInfo;
import com.example.platformtriage.model.dto.ServiceInfo;
import com.example.platformtriage.model.dto.EndpointsInfo;

import java.util.List;

public record Objects(
    List<Workload> deployments,
    List<PodInfo> pods,
    List<EventInfo> events,
    List<ServiceInfo> services,
    List<EndpointsInfo> endpoints
) {}