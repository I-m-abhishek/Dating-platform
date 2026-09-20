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

import java.time.Instant;
import java.util.UUID;

/**
 * A mutual connection between two users.
 *
 * <p><b>Canonical ordering.</b> {@code userAId} is always the lexicographically smaller
 * UUID. That makes the unique constraint able to enforce "one match per pair" without a
 * second index or an application-level lock - the alternative (storing the pair in swipe
 * order) allows duplicate matches under concurrency.
 */
@Entity
@Table(name = "matches",
        uniqueConstraints = @UniqueConstraint(name = "uk_matches_pair", columnNames = {"user_a_id", "user_b_id"}),
        indexes = {
                @Index(name = "idx_matches_user_a", columnList = "user_a_id,status"),
                @Index(name = "idx_matches_user_b", columnList = "user_b_id,status"),
                @Index(name = "idx_matches_matched_at", columnList = "matched_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match extends BaseUuidEntity {

    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    @Builder.Default
    private MatchSource source = MatchSource.MUTUAL_LIKE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MatchStatus status = MatchStatus.ACTIVE;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    @Column(name = "compatibility_score")
    private Double compatibilityScore;

    /** Human readable reasons from the scorer, shown on the match card. */
    @Column(name = "highlights", length = 400)
    private String highlights;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "unmatched_by")
    private UUID unmatchedBy;

    @Column(name = "unmatched_at")
    private Instant unmatchedAt;

    @Column(name = "last_interaction_at")
    private Instant lastInteractionAt;

    /** Canonical ordering helper - always build matches through this. */
    public static Match between(UUID one, UUID two) {
        boolean oneFirst = compareAsPostgresDoes(one, two) <= 0;
        return Match.builder()
                .userAId(oneFirst ? one : two)
                .userBId(oneFirst ? two : one)
                .matchedAt(Instant.now())
                .build();
    }

    /**
     * Orders two UUIDs the way PostgreSQL does.
     *
     * <p><b>Do not replace this with {@link UUID#compareTo}.</b> That compares the two
     * halves as <em>signed</em> longs, while PostgreSQL compares a {@code uuid} as sixteen
     * <em>unsigned</em> bytes. The two disagree whenever the top bit of the most significant
     * half differs, which is true of roughly half of all random UUID pairs.
     *
     * <p>The {@code ck_matches_ordering} check constraint is enforced by PostgreSQL, so the
     * canonical ordering has to use PostgreSQL's definition. Using Java's caused inserts to
     * be rejected for about half of all matches.
     */
    public static int compareAsPostgresDoes(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0
                ? high
                : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    public UUID otherParticipant(UUID userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public boolean involves(UUID userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }
}
