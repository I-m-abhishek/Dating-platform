package com.dating.platform.match.dto;

import com.dating.platform.match.entity.AutoMatchRun;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Audit view of one auto-match attempt. Entities never leave the service layer. */
@Schema(name = "AutoMatchRunResponse")
public record AutoMatchRunResponse(
        UUID id,
        AutoMatchRun.Cadence cadence,
        String periodKey,
        AutoMatchRun.Outcome outcome,
        UUID matchedUserId,
        UUID matchId,
        Double score,
        int candidatesConsidered,
        String note,
        Instant ranAt
) {

    public static AutoMatchRunResponse from(AutoMatchRun run) {
        return new AutoMatchRunResponse(run.getId(), run.getCadence(), run.getPeriodKey(),
                run.getOutcome(), run.getMatchedUserId(), run.getMatchId(), run.getScore(),
                run.getCandidatesConsidered(), run.getNote(), run.getCreatedAt());
    }
}
