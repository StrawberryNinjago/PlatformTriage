package com.example.platformtriage.model.dto;

import java.util.List;
import java.util.Map;

public record ServiceInfo(
    String name,
    String type,
    Map<String, String> selector,
    List<String> ports
) {}
