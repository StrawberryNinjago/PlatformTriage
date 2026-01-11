package com.example.platformtriage.detection;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.example.platformtriage.model.enums.FailureCode;

/**
 * Pattern-based event → finding mapper.
 * 
 * Rules are evaluated in order, first match wins.
 * This gives us:
 * - Explicit precedence (CSI errors before generic mount errors)
 * - Easy to add new patterns
 * - Centralized mapping logic
 */
public final class DefaultEventFindingMapper implements EventFindingMapper {
    
    private final List<MappingRule> rules = List.of(
        // === EXTERNAL_SECRET_RESOLUTION_FAILED (CSI / Key Vault) ===
        // Must come before generic FailedMount rules
        MappingRule.reason("FailedMount")
            .and(msg -> msg.contains("secrets-store.csi") 
                || msg.contains("secretproviderclass")
                || msg.contains("keyvault") 
                || msg.contains("key vault"))
            .to(new MappedFailure(
                FailureCode.EXTERNAL_SECRET_RESOLUTION_FAILED,
                "External secret mount failed (CSI / Key Vault)"
            )),
        
        MappingRule.reason("FailedAttachVolume")
            .and(msg -> msg.contains("secrets-store") || msg.contains("keyvault"))
            .to(new MappedFailure(
                FailureCode.EXTERNAL_SECRET_RESOLUTION_FAILED,
                "External secret mount failed (CSI / Key Vault)"
            )),
        
        // === BAD_CONFIG (non-CSI secret/configmap missing) ===
        MappingRule.reason("FailedMount")
            .and(msg -> (msg.contains("secret") || msg.contains("configmap")) 
                && msg.contains("not found"))
            .to(new MappedFailure(
                FailureCode.BAD_CONFIG,
                "Bad configuration"
            )),
        
        // === IMAGE_PULL_FAILED ===
        MappingRule.reason("Failed")
            .and(msg -> msg.contains("pull") || msg.contains("ErrImagePull"))
            .to(new MappedFailure(
                FailureCode.IMAGE_PULL_FAILED,
                "Image pull failed"
            )),
        
        MappingRule.reason("ErrImagePull")
            .to(new MappedFailure(
                FailureCode.IMAGE_PULL_FAILED,
                "Image pull failed"
            )),
        
        MappingRule.reason("ImagePullBackOff")
            .to(new MappedFailure(
                FailureCode.IMAGE_PULL_FAILED,
                "Image pull failed"
            )),
        
        // === CRASH_LOOP ===
        MappingRule.reason("BackOff")
            .and(msg -> msg.contains("restarting failed container") 
                || msg.contains("back-off"))
            .to(new MappedFailure(
                FailureCode.CRASH_LOOP,
                "Crash loop detected"
            )),
        
        // === INSUFFICIENT_RESOURCES ===
        MappingRule.reason("FailedScheduling")
            .to(new MappedFailure(
                FailureCode.INSUFFICIENT_RESOURCES,
                "Insufficient resources"
            )),
        
        MappingRule.anyReason()
            .and(msg -> msg.contains("insufficient cpu") 
                || msg.contains("insufficient memory")
                || msg.contains("unschedulable"))
            .to(new MappedFailure(
                FailureCode.INSUFFICIENT_RESOURCES,
                "Insufficient resources"
            )),
        
        // === RBAC_DENIED ===
        MappingRule.anyReason()
            .and(msg -> msg.contains("forbidden") 
                || msg.contains("rbac")
                || msg.contains("unauthorized")
                || msg.contains("access denied")
                || msg.contains("permission denied"))
            .to(new MappedFailure(
                FailureCode.RBAC_DENIED,
                "RBAC permission denied"
            )),
        
        // === POD_SANDBOX_RECYCLE (risk signal) ===
        MappingRule.reason("SandboxChanged")
            .to(new MappedFailure(
                FailureCode.POD_SANDBOX_RECYCLE,
                "Pod sandbox recycled"
            ))
    );
    
    @Override
    public Optional<MappedFailure> map(EventView event) {
        if (!event.isWarning()) {
            return Optional.empty(); // Only map warning events
        }
        
        return rules.stream()
            .filter(rule -> rule.matches(event))
            .findFirst()
            .map(MappingRule::mappedFailure);
    }
    
    /**
     * Fluent builder for event matching rules.
     */
    private static class MappingRule {
        private final Predicate<EventView> predicate;
        private final MappedFailure mappedFailure;
        
        private MappingRule(Predicate<EventView> predicate, MappedFailure mappedFailure) {
            this.predicate = predicate;
            this.mappedFailure = mappedFailure;
        }
        
        public boolean matches(EventView event) {
            return predicate.test(event);
        }
        
        public MappedFailure mappedFailure() {
            return mappedFailure;
        }
        
        // === Builder methods ===
        
        public static ReasonBuilder reason(String reason) {
            return new ReasonBuilder(reason);
        }
        
        public static ReasonBuilder anyReason() {
            return new ReasonBuilder(null);
        }
        
        public static class ReasonBuilder {
            private final String reason;
            private Predicate<String> messagePredicate = msg -> true;
            
            private ReasonBuilder(String reason) {
                this.reason = reason;
            }
            
            public ReasonBuilder and(Predicate<String> messagePredicate) {
                this.messagePredicate = messagePredicate;
                return this;
            }
            
            public MappingRule to(MappedFailure mappedFailure) {
                Predicate<EventView> predicate = event -> {
                    // Check reason
                    if (reason != null && !reason.equalsIgnoreCase(event.reason())) {
                        return false;
                    }
                    // Check message
                    String msg = event.message() != null ? event.message().toLowerCase() : "";
                    return messagePredicate.test(msg);
                };
                
                return new MappingRule(predicate, mappedFailure);
            }
        }
    }
}
