package com.dating.platform.match.engine;

import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.Quality;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scorer decides who meets whom, so its behaviour is pinned down here rather than
 * left to observation in production.
 */
class CompatibilityScorerTest {

    private final CompatibilityScorer scorer = new CompatibilityScorer();

    @Test
    @DisplayName("identical people score higher than opposites")
    void identicalBeatsOpposite() {
        User alex = user(30, 25, 35, 50, 12.97, 77.59);
        User sam = user(29, 25, 35, 50, 12.98, 77.60);
        User jordan = user(48, 45, 60, 50, 40.71, -74.00);

        Profile alexProfile = profile(0.9, RelationshipIntent.LONG_TERM, ChildrenPreference.WANT_SOMEDAY,
                Set.of("hiking", "coffee", "film"), Set.of());
        Profile samProfile = profile(0.9, RelationshipIntent.LONG_TERM, ChildrenPreference.WANT_SOMEDAY,
                Set.of("hiking", "coffee", "film"), Set.of());
        Profile jordanProfile = profile(0.2, RelationshipIntent.NEW_FRIENDS, ChildrenPreference.DONT_WANT,
                Set.of("chess"), Set.of());

        double close = scorer.score(alex, alexProfile, sam, samProfile).total();
        double far = scorer.score(alex, alexProfile, jordan, jordanProfile).total();

        assertThat(close).isGreaterThan(far);
        assertThat(close).isBetween(0d, 1d);
        assertThat(far).isBetween(0d, 1d);
    }

    @Test
    @DisplayName("scoring is symmetric - both people see the same number")
    void symmetric() {
        User a = user(30, 25, 40, 60, 12.97, 77.59);
        User b = user(33, 28, 38, 60, 13.01, 77.62);
        Profile pa = profile(0.7, RelationshipIntent.LONG_TERM, ChildrenPreference.OPEN_TO_CHILDREN,
                Set.of("running", "wine"), Set.of());
        Profile pb = profile(0.8, RelationshipIntent.LONG_TERM_OPEN_TO_SHORT, ChildrenPreference.NOT_SURE,
                Set.of("running", "travel"), Set.of());

        assertThat(scorer.score(a, pa, b, pb).total())
                .isEqualTo(scorer.score(b, pb, a, pa).total());
    }

    @Test
    @DisplayName("an empty profile still scores inside the valid range")
    void emptyProfileIsSafe() {
        User a = user(30, 25, 40, 60, 12.97, 77.59);
        User b = user(31, 25, 40, 60, 12.97, 77.59);
        Profile empty = profile(0d, null, null, Set.of(), Set.of());

        CompatibilityScore score = scorer.score(a, empty, b, empty);

        assertThat(score.total()).isBetween(0d, 1d);
        assertThat(score.breakdown()).containsKeys("interests", "qualities", "intent", "lifestyle");
    }

    @Test
    @DisplayName("shared interests are reported for the match card")
    void sharedInterests() {
        Profile a = profile(0.5, null, null, Set.of("hiking", "coffee"), Set.of());
        Profile b = profile(0.5, null, null, Set.of("coffee", "chess"), Set.of());

        assertThat(scorer.sharedInterestLabels(a, b)).containsExactly("coffee");
    }

    @Test
    @DisplayName("wanting children versus not wanting them drags the score down")
    void childrenMismatchHurts() {
        User a = user(30, 25, 40, 60, 12.97, 77.59);
        User b = user(31, 25, 40, 60, 12.97, 77.59);
        Set<String> sameInterests = Set.of("hiking", "coffee");

        Profile wants = profile(0.8, RelationshipIntent.LONG_TERM, ChildrenPreference.WANT_SOMEDAY,
                sameInterests, Set.of());
        Profile doesNot = profile(0.8, RelationshipIntent.LONG_TERM, ChildrenPreference.DONT_WANT,
                sameInterests, Set.of());

        double agreeing = scorer.score(a, wants, b, wants).total();
        double conflicting = scorer.score(a, wants, b, doesNot).total();

        assertThat(conflicting).isLessThan(agreeing);
    }

    // ---- fixtures ------------------------------------------------------

    private User user(int age, int minAge, int maxAge, int distanceKm, double lat, double lon) {
        return User.builder()
                .displayName("Test")
                .dateOfBirth(LocalDate.now().minusYears(age))
                .gender(Gender.WOMAN)
                .preferredMinAge(minAge)
                .preferredMaxAge(maxAge)
                .preferredMaxDistanceKm(distanceKm)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    private Profile profile(double completeness, RelationshipIntent intent, ChildrenPreference children,
                            Set<String> interestSlugs, Set<String> qualitySlugs) {
        Set<Interest> interests = interestSlugs.stream()
                .map(slug -> Interest.builder().slug(slug).label(slug).category("test").build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Quality> qualities = qualitySlugs.stream()
                .map(slug -> Quality.builder().slug(slug).label(slug).dimension(slug).affinityWeight(1.0).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return Profile.builder()
                .completeness(completeness)
                .relationshipIntent(intent)
                .children(children)
                .interests(interests)
                .qualities(qualities)
                .languages(new LinkedHashSet<>(Arrays.asList("English")))
                .build();
    }
}
