package com.example.platformtriage.model.enums;

/**
 * Severity levels for findings.
 * ERROR = critical failure blocking operation
 * WARN = non-blocking issue that should be addressed
 * INFO = informational message
 * 
 * Legacy: HIGH, MED, LOW (mapped to ERROR, WARN, INFO)
 */
public enum Severity {
  ERROR,  // Critical failure (was HIGH)
  WARN,   // Warning (was MED)
  INFO,   // Informational (was LOW/INFO)
  
  // Legacy compatibility
  HIGH,   // Use ERROR instead
  MED,    // Use WARN instead
  LOW     // Use INFO instead
}
