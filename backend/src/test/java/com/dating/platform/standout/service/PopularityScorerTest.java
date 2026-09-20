package com.dating.platform.standout.service;

import com.dating.platform.profile.entity.Profile;
import com.dating.platform.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PopularityScorerTest {

    private final PopularityScorer scorer = new PopularityScorer();

    @Test
    @DisplayName("popularity is log-scaled so a runaway favourite cannot own the shelf")
    void popularityIsLogScaled() {
        User user = activeUser(60);
        Profile profile = profile(0.9);

        double at10 = scorer.score(user, profile, 10, 0).score();
        double at100 = scorer.score(user, profile, 100, 0).score();
        double at1000 = scorer.score(user, profile, 1000, 0).score();

        assertThat(at100 - at10).isGreaterThan(at1000 - at100);
        assertThat(at1000).isLessThanOrEqualTo(1d);
    }

    @Test
    @DisplayName("a brand new account can stand out with no likes at all")
    void newcomersGetABoost() {
        User newcomer = User.builder()
                .displayName("New")
                .lastActiveAt(Instant.now())
                .build();
        newcomer.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        PopularityScorer.Scored scored = scorer.score(newcomer, profile(0.8), 0, 0);

        assertThat(scored.score()).isGreaterThan(0d);
        assertThat(scored.reason()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("an inactive account with a thin profile scores near zero")
    void inactiveAndEmptyScoresLow() {
        User stale = User.builder()
                .displayName("Stale")
                .lastActiveAt(Instant.now().minus(120, ChronoUnit.DAYS))
                .build();
        stale.setCreatedAt(Instant.now().minus(400, ChronoUnit.DAYS));

        assertThat(scorer.score(stale, profile(0d), 0, 0).score()).isEqualTo(0d);
    }

    @Test
    @DisplayName("super likes count for more than ordinary likes")
    void superLikesWeighMore() {
        User user = activeUser(30);
        Profile profile = profile(0.5);

        double ordinary = scorer.score(user, profile, 10, 0).score();
        double withSupers = scorer.score(user, profile, 10, 5).score();

        assertThat(withSupers).isGreaterThan(ordinary);
    }

    private User activeUser(int accountAgeDays) {
        User user = User.builder()
                .displayName("Test")
                .lastActiveAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
        user.setCreatedAt(Instant.now().minus(accountAgeDays, ChronoUnit.DAYS));
        return user;
    }

    private Profile profile(double completeness) {
        return Profile.builder().completeness(completeness).build();
    }
}
