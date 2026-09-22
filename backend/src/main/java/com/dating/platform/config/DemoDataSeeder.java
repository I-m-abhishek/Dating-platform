package com.dating.platform.config;

import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.Prompt;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.entity.Quality;
import com.dating.platform.profile.repository.InterestRepository;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.profile.repository.PromptRepository;
import com.dating.platform.profile.repository.QualityRepository;
import com.dating.platform.profile.service.ProfileService;
import com.dating.platform.user.entity.enums.Role;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds a small cast of accounts for local development.
 *
 * <p>Gated on {@code app.seed-demo-data}, which is true only in the dev profile. The flag
 * has existed in {@link AppProperties} since the beginning but nothing read it, so the
 * setting did nothing - this is the missing half.
 *
 * <p><b>Idempotent.</b> Every account is keyed on its email and skipped if it already
 * exists, so restarting the app does not duplicate anything and editing one of these
 * profiles by hand will not be undone on the next boot.
 *
 * <p><b>The ages and preferences are chosen, not arbitrary.</b> Discovery filters age in
 * both directions: a candidate's age must sit inside the viewer's range <em>and</em> the
 * viewer's age inside the candidate's. Seed data that ignores that produces empty decks and
 * looks like a bug in the app. Everyone here is 24-31 and looking for 21-40, so every pair
 * satisfies both halves. Likewise the coordinates are a few kilometres apart, well inside
 * the 80 km default radius.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PhotoRepository photoRepository;
    private final PromptRepository promptRepository;
    private final PromptAnswerRepository promptAnswerRepository;
    private final InterestRepository interestRepository;
    private final QualityRepository qualityRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    /** Bengaluru, with a few kilometres between each person. */
    private static final double BASE_LAT = 12.9716;
    private static final double BASE_LON = 77.5946;

    private record DemoUser(
            String displayName,
            String email,
            String password,
            Gender gender,
            int age,
            String jobTitle,
            String school,
            int heightCm,
            String bio,
            RelationshipIntent intent,
            LifestyleChoice drinking,
            LifestyleChoice smoking,
            List<String> interestSlugs,
            List<String> qualitySlugs,
            List<String> promptAnswers
    ) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("Abhi", "abhi@google.com", "Abhi@1234", Gender.MAN, 28,
                    "Backend Engineer", "BITS Pilani", 178,
                    "Building things that stay up at 3am. Will argue about database indexes and the correct way to make filter coffee.",
                    RelationshipIntent.LONG_TERM, LifestyleChoice.SOMETIMES, LifestyleChoice.NO,
                    List.of("hiking", "running", "live-music"),
                    List.of("ambitious", "analytical"),
                    List.of("Shipping something a million people use.",
                            "Debugging a race condition nobody else believed existed.")),

            new DemoUser("Shiva", "shiva@google.com", "Shiva@1234", Gender.MAN, 30,
                    "Product Designer", "NID Ahmedabad", 174,
                    "I redraw the same screen forty times and the fortieth is always better. Cook badly, cycle far.",
                    RelationshipIntent.LONG_TERM_OPEN_TO_SHORT, LifestyleChoice.SOMETIMES, LifestyleChoice.NO,
                    List.of("cycling", "photography", "coffee"),
                    List.of("creative", "grounded"),
                    List.of("Cycling the length of the Western Ghats.",
                            "Kerning. I am not proud of it.")),

            new DemoUser("Adi", "adi@google.com", "Adi@1234", Gender.MAN, 26,
                    "Data Scientist", "IIT Madras", 181,
                    "Mostly notebooks and bouldering chalk. Ask me why your A/B test is underpowered.",
                    RelationshipIntent.FIGURING_IT_OUT, LifestyleChoice.NO, LifestyleChoice.NO,
                    List.of("climbing", "reading", "board-games"),
                    List.of("curious", "dry-humour"),
                    List.of("Reading one genuinely difficult book a month.",
                            "Board games. I will not go easy on you.")),

            new DemoUser("Pushan", "pushan@google.com", "Pushan@1234", Gender.MAN, 31,
                    "Chef", "Culinary Institute", 176,
                    "Twelve hours on my feet and I still want to cook for you. Sunday is for markets and slow food.",
                    RelationshipIntent.LONG_TERM, LifestyleChoice.YES, LifestyleChoice.NO,
                    List.of("cooking", "street-food", "live-music"),
                    List.of("easygoing", "extroverted"),
                    List.of("Opening a place that seats twelve and does one menu.",
                            "A market, a long lunch, and absolutely no plans after.")),

            new DemoUser("Krishti", "krishti@google.com", "Krishti@1234", Gender.WOMAN, 27,
                    "Architect", "CEPT University", 165,
                    "I notice ceilings before I notice people. Sorry in advance. Long walks, longer arguments about cities.",
                    RelationshipIntent.LONG_TERM, LifestyleChoice.SOMETIMES, LifestyleChoice.NO,
                    List.of("art-galleries", "travel", "coffee"),
                    List.of("creative", "independent"),
                    List.of("Designing a building someone grows up in.",
                            "Walking a whole city instead of taking the metro.")),

            new DemoUser("Khushi", "khushi@google.com", "Khushi@1234", Gender.WOMAN, 25,
                    "Doctor", "AIIMS Delhi", 162,
                    "Long shifts, short temper for bad coffee. Off days are for yoga and doing absolutely nothing.",
                    RelationshipIntent.LONG_TERM_OPEN_TO_SHORT, LifestyleChoice.NO, LifestyleChoice.NO,
                    List.of("yoga", "running", "cooking"),
                    List.of("empathetic", "organised"),
                    List.of("Finishing my residency without losing my sense of humour.",
                            "Anyone who says they like hospital food.")),

            new DemoUser("Zuberiya", "zuberiya@google.com", "Zuberiya@1234", Gender.WOMAN, 29,
                    "Journalist", "Jamia Millia Islamia", 168,
                    "I ask too many questions. It is literally the job. Bookshops, biryani, and a notebook I never finish.",
                    RelationshipIntent.LONG_TERM, LifestyleChoice.NO, LifestyleChoice.NO,
                    List.of("reading", "photography", "writing"),
                    List.of("curious", "direct"),
                    List.of("Writing the piece that actually changes something.",
                            "Bookshops. I am not allowed in unsupervised.")));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long created = DEMO_USERS.stream().filter(this::seed).count();
        if (created > 0) {
            log.info("Seeded {} demo account(s). Password format is Name@1234.", created);
        } else {
            log.info("Demo accounts already present - nothing seeded.");
        }
    }

    private boolean seed(DemoUser demo) {
        if (userRepository.existsByEmailIgnoreCase(demo.email())) {
            return false;
        }

        int index = DEMO_USERS.indexOf(demo);
        // Fans the cast out around the city centre so distances are non-zero but close.
        double lat = BASE_LAT + ((index % 3) - 1) * 0.02;
        double lon = BASE_LON + ((index % 4) - 2) * 0.02;

        User user = userRepository.save(User.builder()
                .email(demo.email().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(demo.password()))
                .displayName(demo.displayName())
                .dateOfBirth(LocalDate.now().minusYears(demo.age()).minusDays(30L * index))
                .gender(demo.gender())
                // Straight pairings, purely so the seven accounts can actually see each
                // other during testing. Nothing in the app assumes this.
                .interestedIn(demo.gender() == Gender.MAN
                        ? EnumSet.of(Gender.WOMAN)
                        : EnumSet.of(Gender.MAN))
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.USER))
                .latitude(lat)
                .longitude(lon)
                .city("Bengaluru")
                .country("IN")
                .preferredMinAge(21)
                .preferredMaxAge(40)
                .preferredMaxDistanceKm(80)
                .emailVerified(true)
                .photoVerified(true)
                .lastActiveAt(Instant.now())
                .onboardingCompletedAt(Instant.now())
                .build());

        Profile profile = Profile.builder()
                .user(user)
                .bio(demo.bio())
                .jobTitle(demo.jobTitle())
                .school(demo.school())
                .hometown("Bengaluru")
                .heightCm(demo.heightCm())
                .relationshipIntent(demo.intent())
                .drinking(demo.drinking())
                .smoking(demo.smoking())
                .build();
        profile.setLanguages(new LinkedHashSet<>(List.of("English", "Hindi")));
        profile.setInterests(lookupInterests(demo.interestSlugs()));
        profile.setQualities(lookupQualities(demo.qualitySlugs()));
        profileRepository.save(profile);

        attachPromptAnswers(user, demo.promptAnswers());
        attachPlaceholderPhoto(user);

        profileService.recalculateCompleteness(user.getId());
        log.info("Seeded demo account {} <{}>", demo.displayName(), demo.email());
        return true;
    }

    private Set<Interest> lookupInterests(List<String> slugs) {
        Set<Interest> found = new LinkedHashSet<>(interestRepository.findAllByActiveTrueOrderByCategoryAscLabelAsc()
                .stream()
                .filter(interest -> slugs.contains(interest.getSlug()))
                .toList());
        if (found.size() < slugs.size()) {
            // The reference tables come from V2; a slug that is not there is a data drift
            // bug, not a reason to fail a dev boot.
            log.debug("Some demo interest slugs were not found: {}", slugs);
        }
        return found;
    }

    private Set<Quality> lookupQualities(List<String> slugs) {
        return new LinkedHashSet<>(qualityRepository.findAll().stream()
                .filter(quality -> slugs.contains(quality.getSlug()))
                .toList());
    }

    private void attachPromptAnswers(User user, List<String> answers) {
        List<Prompt> prompts = promptRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .limit(answers.size())
                .toList();

        for (int i = 0; i < prompts.size() && i < answers.size(); i++) {
            promptAnswerRepository.save(PromptAnswer.builder()
                    .user(user)
                    .prompt(prompts.get(i))
                    .answer(answers.get(i))
                    .displayOrder(i)
                    .build());
        }
    }

    /**
     * Writes one generated placeholder image so the seeded profiles are usable in a
     * photo-led UI.
     *
     * <p>Plainly a placeholder - a gradient and an initial, the same idea as the initials
     * avatar the web client falls back to. It is not a stand-in for a real person, and it
     * is not pretending to be one.
     */
    private void attachPlaceholderPhoto(User user) {
        try {
            byte[] png = renderPlaceholder(user.getDisplayName());

            String key = "photos/" + user.getId() + "/" + UUID.randomUUID() + ".png";
            Path target = Paths.get(appProperties.storage().localRoot())
                    .toAbsolutePath()
                    .normalize()
                    .resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, png);

            String base = appProperties.storage().publicBaseUrl();
            photoRepository.save(Photo.builder()
                    .user(user)
                    .storageKey(key)
                    .url((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + key)
                    .width(900)
                    .height(1200)
                    .displayOrder(0)
                    .primaryPhoto(true)
                    .build());
        } catch (IOException e) {
            // A missing demo photo is cosmetic. Never let it stop the app from starting.
            log.warn("Could not write a placeholder photo for {}", user.getDisplayName(), e);
        }
    }

    private byte[] renderPlaceholder(String name) throws IOException {
        int width = 900;
        int height = 1200;
        int hue = Math.floorMod(name.hashCode(), 360);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setPaint(new GradientPaint(
                0, 0, Color.getHSBColor(hue / 360f, 0.55f, 0.82f),
                width, height, Color.getHSBColor(((hue + 48) % 360) / 360f, 0.62f, 0.55f)));
        g.fillRect(0, 0, width, height);

        String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        g.setColor(new Color(255, 255, 255, 220));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 320));
        int textWidth = g.getFontMetrics().stringWidth(initial);
        g.drawString(initial, (width - textWidth) / 2, height / 2 + 110);

        g.setColor(new Color(255, 255, 255, 150));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
        String caption = "demo profile";
        int captionWidth = g.getFontMetrics().stringWidth(caption);
        g.drawString(caption, (width - captionWidth) / 2, height - 90);

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
