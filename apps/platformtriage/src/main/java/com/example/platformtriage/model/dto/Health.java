package com.example.platformtriage.model.dto;

import com.example.platformtriage.model.enums.OverallStatus;
import java.util.Map;

public record Health(
      OverallStatus overall,
      String deploymentsReady, // e.g. "1/2"
      Map<String, Integer> pods // keys: running, pending, crashLoop, imagePullBackOff, notReady
  ) {}