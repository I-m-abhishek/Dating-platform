package com.dating.platform.standout.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A precomputed Standouts ranking for one refresh cycle.
 *
 * <p>Standouts are read constantly and change slowly, so the ranking is computed on a
 * schedule and stored, not derived per request. {@code cycleKey} is the refresh timestamp
 * bucket, which also gives us a free cache key and a trivial rollback (point reads at the
 * previous cycle).
 */
@Entity
@Table(name = "standout_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_standout_snapshots_scope",
                columnNames = {"cycle_key", "user_id"}),
        indexes = {
                @Index(name = "idx_standout_snapshots_cycle", columnList = "cycle_key,rank_position"),
                @Index(name = "idx_standout_snapshots_city", columnList = "cycle_key,city")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandoutSnapshot extends BaseUuidEntity {

    @Column(name = "cycle_key", nullable = false, length = 20)
    private String cycleKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    /** Why this person is standing out, e.g. {@code POPULAR}, {@code NEW}, {@code ACTIVE}. */
    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @Column(name = "likes_received", nullable = false)
    @Builder.Default
    private int likesReceived = 0;
}
