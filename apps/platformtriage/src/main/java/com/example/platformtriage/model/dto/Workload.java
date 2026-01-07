package com.example.platformtriage.model.dto;

import java.util.List;

public record Workload(
      String name,
      String kind,          // Deployment / StatefulSet (MVP can be just Deployment)
      String ready,         // "0/1"
      List<String> conditions
  ) {}
  