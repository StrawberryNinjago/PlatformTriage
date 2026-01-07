package com.example.platformtriage.model.dto;

import com.example.platformtriage.model.enums.Severity;
import java.util.List;

public record Finding(
      Severity severity,
      String code,
      String message,
      List<String> hints,
      List<String> evidenceRefs
  ) {}
