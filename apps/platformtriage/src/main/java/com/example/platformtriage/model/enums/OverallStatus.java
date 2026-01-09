package com.example.platformtriage.model.enums;

/**
 * Overall health status for deployment triage.
 * 
 * PASS = All checks passed, deployment is healthy
 * WARN = Non-critical issues detected
 * FAIL = Critical failures detected
 * UNKNOWN = Cannot assess (no matching objects found)
 */
public enum OverallStatus {
  PASS, WARN, FAIL, UNKNOWN
}
