package com.example.platformtriage.model.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Debug metadata explaining why a finding was chosen as the primary failure.
 * 
 * This helps users (and developers) understand the ranking algorithm's decision.
 * 
 * Example:
 * {
 *   "chosenBy": "FindingRanker",
 *   "score": 15,
 *   "scoreBreakdown": {
 *     "severityWeight": 0,
 *     "codePriority": 20,
 *     "blastRadius": 15,
 *     "readinessPenalty": -20,
 *     "totalScore": 15
 *   },
 *   "competingFindings": [
 *     "BAD_CONFIG(15)",
 *     "CRASH_LOOP(60)",
 *     "POD_RESTARTS_DETECTED(150)"
 *   ]
 * }
 */
public record PrimaryFailureDebug(
    @JsonProperty("chosenBy") String chosenBy,
    @JsonProperty("score") int score,
    @JsonProperty("scoreBreakdown") Map<String, Integer> scoreBreakdown,
    @JsonProperty("competingFindings") List<String> competingFindings
) {
    public static PrimaryFailureDebug fromRanker(
        int score,
        Map<String, Integer> scoreBreakdown,
        List<String> competingFindings
    ) {
        return new PrimaryFailureDebug(
            "FindingRanker",
            score,
            scoreBreakdown,
            competingFindings
        );
    }
}
