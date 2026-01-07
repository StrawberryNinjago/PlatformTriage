package com.example.platformtriage.model.dto;

public record PodInfo(
      String name,
      String phase,
      String reason,
      boolean ready,
      int restarts
  ) {}
