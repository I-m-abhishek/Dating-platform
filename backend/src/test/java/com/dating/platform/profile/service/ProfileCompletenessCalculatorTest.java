package com.dating.platform.profile.service;

import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileCompletenessCalculatorTest {

    @Test
    @DisplayName("an empty profile scores zero")
    void emptyIsZero() {
        double score = ProfileCompletenessCalculator.calculate(
                User.builder().build(), Profile.builder().build(), 0, 0);
        assertThat(score).isZero();
    }

    @Test
    @DisplayName("a fully filled profile reaches 1.0")
    void fullIsOne() {
        User user = User.builder().city("Bengaluru").build();
        Profile profile = Profile.builder()
                .bio("Something worth reading")
                .jobTitle("Engineer")
                .heightCm(175)
                .children(ChildrenPreference.WANT_SOMEDAY)
                .relationshipIntent(RelationshipIntent.LONG_TERM)
                .languages(new LinkedHashSet<>(Set.of("English", "Hindi")))
                .interests(interests(6))
                .qualities(new LinkedHashSet<>())
                .build();
        profile.setQualities(new LinkedHashSet<>(
                IntStream.range(0, 3)
                        .mapToObj(i -> com.dating.platform.profile.entity.Quality.builder()
                                .slug("q" + i).label("q" + i).dimension("d" + i).build())
                        .toList()));

        assertThat(ProfileCompletenessCalculator.calculate(user, profile, 6, 3)).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("photos move the needle more than any single text field")
    void photosDominate() {
        User user = User.builder().build();
        Profile withPhotos = Profile.builder().build();
        Profile withBio = Profile.builder().bio("Hello there").build();

        double photosOnly = ProfileCompletenessCalculator.calculate(user, withPhotos, 4, 0);
        double bioOnly = ProfileCompletenessCalculator.calculate(user, withBio, 0, 0);

        assertThat(photosOnly).isGreaterThan(bioOnly);
    }

    private Set<Interest> interests(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Interest.builder().slug("i" + i).label("i" + i).category("c").build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
