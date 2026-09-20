package com.dating.platform.match.dto;

import com.dating.platform.match.entity.AutoMatchRun;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Result of an auto-match attempt, whether it produced a match or not.
 * The client renders the {@code outcome} explicitly rather than treating "no match"
 * as an error - running out of candidates is a normal state, not a failure.
 */
@Schema(name = "AutoMatchResponse")
public record AutoMatchResponse(
        AutoMatchRun.Outcome outcome,
        AutoMatchRun.Cadence cadence,
        String periodKey,
        MatchResponse match,
        Double score,
        int candidatesConsidered,
        Instant nextRunAt,
        String message
) {
}
