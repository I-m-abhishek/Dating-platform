package com.dating.platform.match.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the canonical pair ordering.
 *
 * <p>The {@code ck_matches_ordering} check constraint requires {@code user_a_id < user_b_id}
 * using PostgreSQL's ordering. Getting this wrong rejects roughly half of all match inserts,
 * and only at runtime against a real PostgreSQL - which is exactly how it was found.
 */
class MatchOrderingTest {

    /** The exact pair that broke in production: the first sorts lower in Java, higher in PostgreSQL. */
    private static final UUID HIGH_BIT_SET = UUID.fromString("ecb7076b-9aea-4206-bfd1-5b47a853519d");
    private static final UUID HIGH_BIT_CLEAR = UUID.fromString("4307ce88-33dd-4b9f-8f7a-5b669e0c34b3");

    @Test
    @DisplayName("Java and PostgreSQL disagree about UUID order - we follow PostgreSQL")
    void followsPostgresNotJava() {
        // Java compares signed, so it puts the high-bit-set UUID first.
        assertThat(HIGH_BIT_SET.compareTo(HIGH_BIT_CLEAR)).isNegative();

        // PostgreSQL compares unsigned bytes, so it puts it second. This is the one that counts.
        assertThat(Match.compareAsPostgresDoes(HIGH_BIT_SET, HIGH_BIT_CLEAR)).isPositive();
    }

    @Test
    @DisplayName("the pair that violated the check constraint now orders correctly")
    void regressionForRejectedInsert() {
        Match match = Match.between(HIGH_BIT_SET, HIGH_BIT_CLEAR);

        assertThat(match.getUserAId()).isEqualTo(HIGH_BIT_CLEAR);
        assertThat(match.getUserBId()).isEqualTo(HIGH_BIT_SET);
        assertThat(match.getUserAId().toString()).isLessThan(match.getUserBId().toString());
    }

    @Test
    @DisplayName("ordering does not depend on which side liked first")
    void isSymmetric() {
        Match forwards = Match.between(HIGH_BIT_SET, HIGH_BIT_CLEAR);
        Match backwards = Match.between(HIGH_BIT_CLEAR, HIGH_BIT_SET);

        assertThat(forwards.getUserAId()).isEqualTo(backwards.getUserAId());
        assertThat(forwards.getUserBId()).isEqualTo(backwards.getUserBId());
    }

    @Test
    @DisplayName("a thousand random pairs all satisfy the check constraint")
    void randomPairsAlwaysSatisfyTheConstraint() {
        for (int i = 0; i < 1000; i++) {
            Match match = Match.between(UUID.randomUUID(), UUID.randomUUID());

            // The constraint is `user_a_id < user_b_id` in PostgreSQL's ordering, which for
            // the canonical hex form is plain lexicographic string comparison.
            assertThat(match.getUserAId().toString())
                    .as("iteration %d", i)
                    .isLessThan(match.getUserBId().toString());
        }
    }

    @Test
    @DisplayName("participant helpers work whichever slot a user landed in")
    void participantHelpers() {
        Match match = Match.between(HIGH_BIT_SET, HIGH_BIT_CLEAR);

        assertThat(match.involves(HIGH_BIT_SET)).isTrue();
        assertThat(match.involves(HIGH_BIT_CLEAR)).isTrue();
        assertThat(match.involves(UUID.randomUUID())).isFalse();
        assertThat(match.otherParticipant(HIGH_BIT_SET)).isEqualTo(HIGH_BIT_CLEAR);
        assertThat(match.otherParticipant(HIGH_BIT_CLEAR)).isEqualTo(HIGH_BIT_SET);
    }
}
