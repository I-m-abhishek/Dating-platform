package com.dating.platform.discovery.service;

import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins down what each discovery filter lets through, including unanswered profile fields. */
class FeedFilterMatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private final UUID hikingId = UUID.randomUUID();

    private User user(boolean verified, Duration lastActiveAgo) {
        User user = User.builder().gender(Gender.WOMAN).photoVerified(verified).build();
        user.setLastActiveAt(lastActiveAgo == null ? null : NOW.minus(lastActiveAgo));
        return user;
    }

    private Profile profile() {
        Interest hiking = Interest.builder().slug("hiking").label("Hiking").build();
        hiking.setId(hikingId);
        return Profile.builder()
                .heightCm(170)
                .relationshipIntent(RelationshipIntent.LONG_TERM)
                .children(ChildrenPreference.WANT_SOMEDAY)
                .drinking(LifestyleChoice.SOMETIMES)
                .smoking(LifestyleChoice.NO)
                .interests(Set.of(hiking))
                .build();
    }

    private static FeedFilterRequest filter(Set<UUID> interests, Set<RelationshipIntent> intents,
                                            Integer minHeight, Integer maxHeight, Boolean verified,
                                            Integer activeHours, Set<ChildrenPreference> children,
                                            Set<LifestyleChoice> drinking, Set<LifestyleChoice> smoking) {
        return new FeedFilterRequest(null, null, null, null, interests, intents, minHeight, maxHeight,
                verified, activeHours, children, drinking, smoking, null);
    }

    private boolean matches(FeedFilterRequest filter) {
        return FeedFilterMatcher.matches(user(true, Duration.ofHours(2)), profile(), filter, NOW);
    }

    @Test
    @DisplayName("an empty filter lets everyone through")
    void emptyFilter() {
        assertThat(matches(FeedFilterRequest.empty())).isTrue();
    }

    @Test
    @DisplayName("looking-for matches any of the chosen intents")
    void intents() {
        assertThat(matches(filter(null, Set.of(RelationshipIntent.LONG_TERM, RelationshipIntent.SHORT_TERM),
                null, null, null, null, null, null, null))).isTrue();
        assertThat(matches(filter(null, Set.of(RelationshipIntent.SHORT_TERM),
                null, null, null, null, null, null, null))).isFalse();
    }

    @Test
    @DisplayName("height is an inclusive range")
    void height() {
        assertThat(matches(filter(null, null, 170, 170, null, null, null, null, null))).isTrue();
        assertThat(matches(filter(null, null, 171, null, null, null, null, null, null))).isFalse();
        assertThat(matches(filter(null, null, null, 169, null, null, null, null, null))).isFalse();
    }

    @Test
    @DisplayName("an unanswered height is excluded by a height filter")
    void missingHeight() {
        Profile noHeight = profile();
        noHeight.setHeightCm(null);
        FeedFilterRequest f = filter(null, null, 150, null, null, null, null, null, null);
        assertThat(FeedFilterMatcher.matches(user(true, Duration.ZERO), noHeight, f, NOW)).isFalse();
    }

    @Test
    @DisplayName("activity keeps only people seen inside the window")
    void activity() {
        FeedFilterRequest today = filter(null, null, null, null, null, 24, null, null, null);
        assertThat(FeedFilterMatcher.matches(user(true, Duration.ofHours(5)), profile(), today, NOW)).isTrue();
        assertThat(FeedFilterMatcher.matches(user(true, Duration.ofHours(30)), profile(), today, NOW)).isFalse();
        assertThat(FeedFilterMatcher.matches(user(true, null), profile(), today, NOW)).isFalse();
    }

    @Test
    @DisplayName("verified-only drops unverified people")
    void verified() {
        FeedFilterRequest f = filter(null, null, null, null, true, null, null, null, null);
        assertThat(FeedFilterMatcher.matches(user(false, Duration.ZERO), profile(), f, NOW)).isFalse();
        assertThat(FeedFilterMatcher.matches(user(true, Duration.ZERO), profile(), f, NOW)).isTrue();
    }

    @Test
    @DisplayName("family plans, drinking and smoking each match any chosen answer")
    void habits() {
        assertThat(matches(filter(null, null, null, null, null, null,
                Set.of(ChildrenPreference.WANT_SOMEDAY), Set.of(LifestyleChoice.SOMETIMES, LifestyleChoice.NO),
                Set.of(LifestyleChoice.NO)))).isTrue();
        assertThat(matches(filter(null, null, null, null, null, null,
                Set.of(ChildrenPreference.DONT_WANT), null, null))).isFalse();
        assertThat(matches(filter(null, null, null, null, null, null,
                null, Set.of(LifestyleChoice.NO), null))).isFalse();
        assertThat(matches(filter(null, null, null, null, null, null,
                null, null, Set.of(LifestyleChoice.YES)))).isFalse();
    }

    @Test
    @DisplayName("interests need at least one in common")
    void interests() {
        assertThat(matches(filter(Set.of(hikingId, UUID.randomUUID()),
                null, null, null, null, null, null, null, null))).isTrue();
        assertThat(matches(filter(Set.of(UUID.randomUUID()),
                null, null, null, null, null, null, null, null))).isFalse();
    }

    @Test
    @DisplayName("only the recommended order is free")
    void sortOrders() {
        for (FeedFilterRequest.SortOrder order : FeedFilterRequest.SortOrder.values()) {
            FeedFilterRequest f = new FeedFilterRequest(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, order);
            assertThat(f.usesAdvancedFilters()).isEqualTo(order != FeedFilterRequest.SortOrder.RECOMMENDED);
        }
        assertThat(FeedFilterRequest.empty().usesAdvancedFilters()).isFalse();
    }

    @Test
    @DisplayName("interests are free; the lifestyle, height and activity filters are paid")
    void freeVersusPaid() {
        assertThat(filter(Set.of(hikingId), null, null, null, null, null, null, null, null)
                .usesAdvancedFilters()).isFalse();
        assertThat(filter(null, null, null, null, null, 24, null, null, null).usesAdvancedFilters()).isTrue();
        assertThat(filter(null, null, null, null, null, null, null, Set.of(LifestyleChoice.NO), null)
                .usesAdvancedFilters()).isTrue();

        FeedFilterRequest stripped = filter(Set.of(hikingId), Set.of(RelationshipIntent.LONG_TERM), 160, 180,
                true, 24, Set.of(ChildrenPreference.WANT_SOMEDAY), Set.of(LifestyleChoice.NO),
                Set.of(LifestyleChoice.NO)).withoutAdvanced();
        assertThat(stripped.usesAdvancedFilters()).isFalse();
        assertThat(stripped.sortOrDefault()).isEqualTo(FeedFilterRequest.SortOrder.RECOMMENDED);
        assertThat(stripped.interestIds()).containsExactly(hikingId);
    }
}
