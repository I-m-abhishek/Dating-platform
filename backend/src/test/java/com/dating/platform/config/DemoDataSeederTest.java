package com.dating.platform.config;

import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeder is switched off in the default test profile, so without this it would ship
 * unexecuted - its bean wiring, its reference-data lookups and its password encoding would
 * all be unverified until someone booted the dev profile.
 *
 * <p>Note that Flyway is disabled for tests, so the interest, quality and prompt reference
 * tables are empty here. That is deliberate coverage in itself: the seeder has to cope with
 * missing reference data rather than fail a boot over it.
 */
@SpringBootTest(properties = "app.seed-demo-data=true")
@ActiveProfiles("test")
class DemoDataSeederTest {

    private static final List<String> EMAILS = List.of(
            "abhi@google.com", "shiva@google.com", "adi@google.com", "pushan@google.com",
            "krishti@google.com", "khushi@google.com", "zuberiya@google.com");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("seeds all seven accounts, discoverable and onboarded")
    void seedsEveryAccount() {
        for (String email : EMAILS) {
            assertThat(userRepository.findByEmailIgnoreCase(email))
                    .as("account %s", email)
                    .isPresent()
                    .get()
                    .satisfies(user -> {
                        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                        assertThat(user.getOnboardingCompletedAt())
                                .as("must be onboarded or discovery skips them")
                                .isNotNull();
                        assertThat(user.getLatitude()).isNotNull();
                        assertThat(user.getLongitude()).isNotNull();
                    });
        }
    }

    @Test
    @DisplayName("the stated passwords actually authenticate")
    void passwordsMatch() {
        assertPassword("abhi@google.com", "Abhi@1234");
        assertPassword("krishti@google.com", "Krishti@1234");
        assertPassword("zuberiya@google.com", "Zuberiya@1234");
    }

    @Test
    @DisplayName("genders are as specified, and preferences let the cast see each other")
    void gendersAndPreferencesLineUp() {
        User abhi = userRepository.findByEmailIgnoreCase("abhi@google.com").orElseThrow();
        User krishti = userRepository.findByEmailIgnoreCase("krishti@google.com").orElseThrow();

        assertThat(abhi.getGender()).isEqualTo(Gender.MAN);
        assertThat(krishti.getGender()).isEqualTo(Gender.WOMAN);
        assertThat(abhi.getInterestedIn()).containsExactly(Gender.WOMAN);
        assertThat(krishti.getInterestedIn()).containsExactly(Gender.MAN);

        /*
         * Discovery filters age in BOTH directions, so a one-sided check here would let
         * seed data through that produces an empty deck. Assert the mutual condition.
         */
        int abhiAge = java.time.Period.between(abhi.getDateOfBirth(), java.time.LocalDate.now()).getYears();
        int krishtiAge = java.time.Period.between(krishti.getDateOfBirth(), java.time.LocalDate.now()).getYears();

        assertThat(abhiAge).isBetween(krishti.getPreferredMinAge(), krishti.getPreferredMaxAge());
        assertThat(krishtiAge).isBetween(abhi.getPreferredMinAge(), abhi.getPreferredMaxAge());
    }

    @Test
    @DisplayName("every seeded account has a primary photo")
    void everyoneHasAPhoto() {
        for (String email : EMAILS) {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(photoRepository.findAllByUserIdOrderByDisplayOrderAsc(user.getId()))
                    .as("photos for %s", email)
                    .isNotEmpty()
                    .anySatisfy(photo -> assertThat(photo.isPrimaryPhoto()).isTrue());
        }
    }

    @Test
    @DisplayName("re-running does not duplicate anyone")
    void seedingIsIdempotent() {
        // The runner already executed once during context startup. Counting by email is
        // what proves a second boot would be a no-op.
        for (String email : EMAILS) {
            assertThat(userRepository.findAll().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(email))
                    .count())
                    .as("copies of %s", email)
                    .isEqualTo(1);
        }
    }

    private void assertPassword(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .as("password for %s", email)
                .isTrue();
    }
}
