package com.example.platformtriage.model.enums;

/**
 * Severity levels for findings.
 * HIGH = critical failure blocking operation
 * MED = non-blocking issue that should be addressed (warnings)
 * INFO = informational message
 */
public enum Severity {
  HIGH,   // Critical failure (blocks operation)
  MED,    // Warning (should be addressed, but not blocking)
  INFO;   // Informational (advisory only)
  
  /**
   * Get explicit rank for severity comparison.
   * Higher rank = more severe.
   */
  public int getRank() {
    return switch (this) {
      case HIGH -> 3;
      case MED -> 2;
      case INFO -> 1;
    };
  }
  
  /**
   * Get the maximum severity from a collection of severities.
   */
  public static Severity max(Severity a, Severity b) {
    if (a == null) return b;
    if (b == null) return a;
    return a.getRank() >= b.getRank() ? a : b;
  }
}
