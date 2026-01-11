package com.example.platformtriage.detection;

import java.util.Optional;

import com.example.platformtriage.model.enums.FailureCode;
import com.example.platformtriage.model.enums.Owner;
import com.example.platformtriage.model.enums.Severity;

/**
 * Single source of truth for mapping Kubernetes events to failure codes.
 * 
 * Centralizes the pattern matching logic (reason, message content) → FailureCode.
 * This makes the mapping:
 * - Consistent (one place to define event → failure mapping)
 * - Testable (unit test just the mapper)
 * - Discoverable (all mappings in one place)
 */
public interface EventFindingMapper {
    /**
     * Map an event to a failure code (if it represents a failure).
     * 
     * @param event The event to map
     * @return MappedFailure if this event represents a known failure, empty otherwise
     */
    Optional<MappedFailure> map(EventView event);
    
    /**
     * Result of mapping an event to a failure.
     * Allows overriding severity/owner from the code's defaults.
     */
    record MappedFailure(
        FailureCode code,
        Severity severity,     // Can override code default
        Owner owner,           // Can override code default
        String titleTemplate   // Template for finding title
    ) {
        /**
         * Use code defaults for severity and owner.
         */
        public MappedFailure(FailureCode code, String titleTemplate) {
            this(code, code.getDefaultSeverity(), code.getDefaultOwner(), titleTemplate);
        }
    }
}
