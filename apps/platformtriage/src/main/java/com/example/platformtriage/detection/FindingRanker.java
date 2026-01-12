package com.example.platformtriage.detection;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.platformtriage.model.dto.Finding;

/**
 * Ranks findings to select the primary failure.
 * 
 * Ranking algorithm considers:
 * 1. Severity weight (ERROR > WARN > INFO)
 * 2. Code priority (from FailureCode.getPriority())
 * 3. Signal strength (blast radius: how many pods affected)
 * 4. Readiness penalty (blocks startup = more critical)
 * 
 * Lower score = higher priority (chosen as primary).
 */
public final class FindingRanker {
    
    /**
     * Rank a finding based on its properties and context signals.
     * 
     * @param finding The finding to rank
     * @param signals Context signals (affected pods, blocks startup, etc.)
     * @return Ranked finding with score and breakdown
     */
    public RankedFinding rank(Finding finding, RankingSignals signals) {
        // Severity weight (HIGH is most critical)
        int severityWeight = switch (finding.severity()) {
            case HIGH -> 0;        // Most critical
            case MED -> 100;       // Medium priority
            case INFO -> 200;      // Least critical
        };
        
        // Code priority (from taxonomy)
        int codePriority = finding.getPriority() * 10;
        
        // Blast radius (how many pods affected, capped at 50)
        int blastRadius = Math.min(50, signals.affectedPods() * 5);
        
        // Readiness penalty (blocks startup = more critical, subtract from score)
        int readinessPenalty = signals.blocksStartup() ? -30 : 0;
        
        // Total score (lower = higher priority)
        int score = severityWeight + codePriority + blastRadius + readinessPenalty;
        
        // Breakdown for debug
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("severityWeight", severityWeight);
        breakdown.put("codePriority", codePriority);
        breakdown.put("blastRadius", blastRadius);
        breakdown.put("readinessPenalty", readinessPenalty);
        breakdown.put("totalScore", score);
        
        return new RankedFinding(finding, score, breakdown);
    }
    
    /**
     * Pick the primary failure from a list of findings.
     * 
     * @param findings All findings detected
     * @param snapshot Cluster snapshot (used to compute signals)
     * @return Primary failure (lowest score) with debug info, or empty if no findings
     */
    public Optional<PrimaryFailureSelection> pickPrimary(List<Finding> findings, ClusterSnapshot snapshot) {
        if (findings.isEmpty()) {
            return Optional.empty();
        }
        
        // Rank all findings
        List<RankedFinding> ranked = findings.stream()
            .map(f -> rank(f, computeSignals(f, snapshot)))
            .sorted(Comparator.comparingInt(RankedFinding::score))
            .toList();
        
        // Pick the one with lowest score
        RankedFinding primary = ranked.get(0);
        
        // Collect competing findings (top 5 for debug)
        List<String> competingCodes = ranked.stream()
            .limit(5)
            .map(rf -> rf.finding().code().name() + "(" + rf.score() + ")")
            .toList();
        
        PrimaryFailureSelection selection = new PrimaryFailureSelection(
            primary.finding(),
            primary.score(),
            primary.scoreBreakdown(),
            competingCodes
        );
        
        return Optional.of(selection);
    }
    
    /**
     * Compute ranking signals for a finding based on snapshot.
     */
    private RankingSignals computeSignals(Finding finding, ClusterSnapshot snapshot) {
        // Count affected pods from evidence
        int affectedPods = (int) finding.evidence().stream()
            .filter(e -> "Pod".equals(e.kind()))
            .count();
        
        // If no pod evidence, use total pod count as upper bound
        if (affectedPods == 0) {
            affectedPods = snapshot.pods().size();
        }
        
        // Determine if it blocks startup (pods not ready)
        boolean blocksStartup = switch (finding.code()) {
            case EXTERNAL_SECRET_RESOLUTION_FAILED,
                 BAD_CONFIG,
                 IMAGE_PULL_FAILED,
                 INSUFFICIENT_RESOURCES,
                 RBAC_DENIED -> true;
            default -> false;
        };
        
        return new RankingSignals(affectedPods, blocksStartup);
    }
    
    /**
     * Signals used for ranking (derived from cluster state).
     */
    public record RankingSignals(
        int affectedPods,
        boolean blocksStartup
    ) {}
    
    /**
     * A finding with its computed rank score and breakdown.
     */
    public record RankedFinding(
        Finding finding,
        int score,
        Map<String, Integer> scoreBreakdown
    ) {}
    
    /**
     * Result of selecting the primary failure.
     * Contains the primary finding plus debug metadata.
     */
    public record PrimaryFailureSelection(
        Finding finding,
        int score,
        Map<String, Integer> scoreBreakdown,
        List<String> competingFindings
    ) {}
}
