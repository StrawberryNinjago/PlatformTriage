package com.example.platformtriage.detection;

import java.time.Clock;

/**
 * Context for detection (immutable, passed to all detectors).
 * 
 * Contains query parameters and utilities (like clock for time-based logic).
 */
public record DetectionContext(
    String namespace,
    String selector,
    String release,
    Clock clock
) {
    public DetectionContext(String namespace, String selector, String release) {
        this(namespace, selector, release, Clock.systemUTC());
    }
}
