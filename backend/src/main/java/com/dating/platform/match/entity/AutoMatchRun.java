package com.dating.platform.match.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Audit row for one auto-match attempt for one user in one period.
 *
 * <p>The unique constraint on (user, cadence, period key) is the idempotency guard: a
 * scheduler retry, a manual trigger and a redeployment mid-run can all happen, and none
 * of them may give the same user two auto-matches in the same week.
 */
@Entity
@Table(name = "auto_match_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_auto_match_runs_scope",
                columnNames = {"user_id", "cadence", "period_key"}),
        indexes = @Index(name = "idx_auto_match_runs_period", columnList = "period_key"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoMatchRun extends BaseUuidEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", nullable = false, length = 10)
    private Cadence cadence;

    /** {@code 2026-W38} for weekly, {@code 2026-09-19} for daily. */
    @Column(name = "period_key", nullable = false, length = 12)
    private String periodKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private Outcome outcome;

    @Column(name = "matched_user_id")
    private UUID matchedUserId;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "score")
    private Double score;

    @Column(name = "candidates_considered", nullable = false)
    @Builder.Default
    private int candidatesConsidered = 0;

    @Column(name = "note", length = 300)
    private String note;

    public enum Cadence {
        WEEKLY,
        DAILY
    }

    public enum Outcome {
        MATCHED,
        NO_CANDIDATE,
        BELOW_THRESHOLD,
        SKIPPED_INELIGIBLE,
        FAILED
    }
}
