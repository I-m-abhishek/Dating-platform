package com.dating.platform.discovery.service;

import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.config.AppProperties;
import com.dating.platform.discovery.dto.FeedCardResponse;
import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.interaction.repository.LikeRepository;
import com.dating.platform.match.engine.CompatibilityScore;
import com.dating.platform.match.engine.CompatibilityScorer;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.mapper.ProfileMapper;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.service.EntitlementService;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import com.dating.platform.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The home feed.
 *
 * <p><b>Shape of the algorithm.</b> SQL narrows the world to a bounded candidate pool
 * (mutual preferences, age, bounding box, not blocked, not already acted on). The pool is
 * then scored and ranked in memory and paged from there.
 *
 * <p>Why not rank in SQL: the compatibility score depends on set overlap and affinity
 * weights that would need several joins and a user-defined function to express, and the
 * pool is capped at a few hundred rows. Keeping it in Java keeps the scoring rules
 * readable and unit-testable. If the pool cap ever has to grow past a few thousand, the
 * right move is a precomputed score table, not a bigger query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private static final Duration RECENTLY_ACTIVE_WINDOW = Duration.ofHours(72);

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PhotoRepository photoRepository;
    private final PromptAnswerRepository promptAnswerRepository;
    private final CandidateFinder candidateFinder;
    private final CompatibilityScorer compatibilityScorer;
    private final EntitlementService entitlementService;
    private final ProfileMapper profileMapper;
    private final LikeRepository likeRepository;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public PageResponse<FeedCardResponse> feed(UUID viewerId, FeedFilterRequest filter, int page, int size) {
        if (filter.usesAdvancedFilters()) {
            entitlementService.require(viewerId, Feature.ADVANCED_FILTERS);
        }

        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", viewerId));
        Profile viewerProfile = profileRepository.findByUserId(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", viewerId));

        // Matches, likes and passes are excluded inside the candidate query itself.
        List<UUID> candidateIds = candidateFinder.findCandidateIds(
                viewer, filter, true, List.of(viewerId), appProperties.matching().candidatePoolSize());
        if (candidateIds.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        Map<UUID, User> users = userRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Profile> profiles = profileRepository.findAllByUserIdIn(candidateIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        Instant now = Instant.now();
        int maxDistanceKm = filter.maxDistanceKm() != null
                ? filter.maxDistanceKm() : viewer.getPreferredMaxDistanceKm();

        List<Scored> scored = candidateIds.stream()
                .map(id -> pairOf(users.get(id), profiles.get(id)))
                .filter(Objects::nonNull)
                .filter(c -> candidateFinder.withinDistance(viewer, c.user(), maxDistanceKm))
                .filter(c -> FeedFilterMatcher.matches(c.user(), c.profile(), filter, now))
                .map(c -> new Scored(c.user(), c.profile(),
                        compatibilityScorer.score(viewer, viewerProfile, c.user(), c.profile())))
                .sorted(comparatorFor(filter, viewer))
                .toList();

        /*
         * The super like priority lane: whoever super liked the viewer goes to the front,
         * whatever the sort, keeping the chosen order within each group. That visibility is
         * what a super like buys.
         */
        Set<UUID> superLikers = scored.isEmpty() ? Set.of() : Set.copyOf(likeRepository.findPendingSuperLikers(
                viewerId, scored.stream().map(s -> s.user().getId()).toList()));
        if (!superLikers.isEmpty()) {
            scored = Stream.concat(
                            scored.stream().filter(s -> superLikers.contains(s.user().getId())),
                            scored.stream().filter(s -> !superLikers.contains(s.user().getId())))
                    .toList();
        }

        int from = Math.min(page * size, scored.size());
        int to = Math.min(from + size, scored.size());
        List<Scored> pageSlice = scored.subList(from, to);

        return PageResponse.of(toCards(viewer, viewerProfile, pageSlice, superLikers), page, size, scored.size());
    }

    // ---- ranking -------------------------------------------------------

    private Comparator<Scored> comparatorFor(FeedFilterRequest filter, User viewer) {
        return switch (filter.sortOrDefault()) {
            case NEWEST -> Comparator.comparing((Scored s) -> s.user().getCreatedAt()).reversed();
            case NEAREST -> Comparator.comparingDouble(s -> distanceOrMax(viewer, s.user()));
            case RECENTLY_ACTIVE -> Comparator.comparing(
                    (Scored s) -> s.user().getLastActiveAt() == null ? Instant.EPOCH : s.user().getLastActiveAt())
                    .reversed();
            // Explicit lambda parameter type: thenComparing(Function, Comparator) cannot infer
            // it from a chained comparator, and the error it produces is unhelpful.
            case RECOMMENDED -> Comparator.comparingDouble((Scored s) -> s.score().total())
                    .reversed()
                    // tie-break on recency so the top of the feed still changes day to day
                    .thenComparing(
                            (Scored s) -> s.user().getLastActiveAt() == null
                                    ? Instant.EPOCH : s.user().getLastActiveAt(),
                            Comparator.<Instant>reverseOrder());
        };
    }

    private double distanceOrMax(User viewer, User candidate) {
        if (viewer.getLatitude() == null || candidate.getLatitude() == null) {
            return Double.MAX_VALUE;
        }
        return GeoUtils.distanceKm(viewer.getLatitude(), viewer.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude());
    }

    // ---- assembly ------------------------------------------------------

    /** Photos and prompts are loaded only for the page being returned, not the whole pool. */
    private List<FeedCardResponse> toCards(User viewer, Profile viewerProfile, List<Scored> slice,
                                           Set<UUID> superLikers) {
        if (slice.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = slice.stream().map(s -> s.user().getId()).toList();

        Map<UUID, List<Photo>> photosByUser = photoRepository.findAllByUserIds(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));
        Map<UUID, List<PromptAnswer>> promptsByUser =
                promptAnswerRepository.findAllByUserIdInOrderByDisplayOrderAsc(ids).stream()
                        .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        List<FeedCardResponse> cards = new ArrayList<>(slice.size());
        for (Scored scored : slice) {
            User user = scored.user();
            Profile profile = scored.profile();
            CompatibilityScore score = scored.score();

            cards.add(new FeedCardResponse(
                    user.getId(),
                    user.getDisplayName(),
                    DateUtils.ageOf(user.getDateOfBirth()),
                    user.getCity(),
                    distanceLabel(viewer, user),
                    profile.getBio(),
                    profile.getJobTitle(),
                    profile.getSchool(),
                    profile.getHeightCm(),
                    profileMapper.toPhotos(photosByUser.getOrDefault(user.getId(), List.of())),
                    profileMapper.toPrompts(promptsByUser.getOrDefault(user.getId(), List.of())),
                    profileMapper.toInterestTags(profile.getInterests()),
                    profileMapper.toQualityTags(profile.getQualities()),
                    compatibilityScorer.sharedInterestLabels(viewerProfile, profile),
                    score.total(),
                    score.highlights(),
                    user.isPhotoVerified(),
                    isRecentlyActive(user),
                    superLikers.contains(user.getId())));
        }
        return cards;
    }

    private Integer distanceLabel(User viewer, User candidate) {
        if (viewer.getLatitude() == null || candidate.getLatitude() == null) {
            return null;
        }
        return GeoUtils.displayDistanceKm(GeoUtils.distanceKm(
                viewer.getLatitude(), viewer.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude()));
    }

    private boolean isRecentlyActive(User user) {
        return user.getLastActiveAt() != null
                && Duration.between(user.getLastActiveAt(), Instant.now()).compareTo(RECENTLY_ACTIVE_WINDOW) < 0;
    }

    private Candidate pairOf(User user, Profile profile) {
        return user == null || profile == null ? null : new Candidate(user, profile);
    }

    private record Candidate(User user, Profile profile) {
    }

    private record Scored(User user, Profile profile, CompatibilityScore score) {
    }
}
