package com.dating.platform.standout.service;

import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.match.engine.CompatibilityScorer;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.mapper.ProfileMapper;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.standout.dto.StandoutResponse;
import com.dating.platform.standout.entity.StandoutSnapshot;
import com.dating.platform.standout.repository.StandoutSnapshotRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import com.dating.platform.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serves the Standouts shelf from the latest precomputed snapshot.
 *
 * <p>Reading is cheap on purpose: one indexed range scan plus bulk hydration. The expensive
 * part (scoring the whole active population) happens in {@link StandoutRefreshService} on a
 * schedule.
 *
 * <p>Personalisation still happens per request: blocked users, existing matches and people
 * already swiped are filtered out, and each tile carries the viewer's own compatibility
 * score. A global leaderboard with no personal filter would show you your own ex.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandoutService {

    private static final int DEFAULT_LIMIT = 12;
    private static final int OVERFETCH_FACTOR = 4;

    private final StandoutSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PhotoRepository photoRepository;
    private final PromptAnswerRepository promptAnswerRepository;
    private final CompatibilityScorer compatibilityScorer;
    private final ProfileMapper profileMapper;

    @Transactional(readOnly = true)
    public List<StandoutResponse> standoutsFor(UUID viewerId, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 50);

        String cycleKey = snapshotRepository.findLatestCycleKey().orElse(null);
        if (cycleKey == null) {
            log.debug("No standout snapshot yet - returning empty shelf");
            return List.of();
        }

        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", viewerId));
        Profile viewerProfile = profileRepository.findByUserId(viewerId).orElse(null);

        // Over-fetch: some rows will be dropped by the gender-preference check below.
        List<StandoutSnapshot> candidates = snapshotRepository.findTopFor(
                cycleKey, viewerId, viewer.getGender(), wantedBy(viewer), Instant.now(),
                PageRequest.of(0, size * OVERFETCH_FACTOR));
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> users = userRepository.findAllById(
                        candidates.stream().map(StandoutSnapshot::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Preference check before hydration, so profiles, photos and prompts are loaded only
        // for the tiles actually returned rather than the whole over-fetch.
        List<StandoutSnapshot> snapshots = candidates.stream()
                .filter(s -> users.containsKey(s.getUserId())
                        && preferencesOverlap(viewer, users.get(s.getUserId())))
                .limit(size)
                .toList();
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = snapshots.stream().map(StandoutSnapshot::getUserId).toList();
        Map<UUID, Profile> profiles = profileRepository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));
        Map<UUID, List<Photo>> photos = photoRepository.findAllByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));
        Map<UUID, List<PromptAnswer>> prompts =
                promptAnswerRepository.findAllByUserIdInOrderByDisplayOrderAsc(userIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        List<StandoutResponse> result = new ArrayList<>(size);
        for (StandoutSnapshot snapshot : snapshots) {
            if (result.size() >= size) {
                break;
            }
            User candidate = users.get(snapshot.getUserId());
            Profile candidateProfile = profiles.get(snapshot.getUserId());
            if (candidate == null || candidateProfile == null) {
                continue;
            }

            double score = viewerProfile == null ? 0d
                    : compatibilityScorer.score(viewer, viewerProfile, candidate, candidateProfile).total();

            result.add(new StandoutResponse(
                    candidate.getId(),
                    candidate.getDisplayName(),
                    DateUtils.ageOf(candidate.getDateOfBirth()),
                    candidate.getCity(),
                    distanceLabel(viewer, candidate),
                    snapshot.getReason(),
                    reasonLabel(snapshot.getReason()),
                    result.size() + 1,
                    score,
                    viewerProfile == null ? List.of()
                            : compatibilityScorer.sharedInterestLabels(viewerProfile, candidateProfile),
                    profileMapper.toPhotos(photos.getOrDefault(candidate.getId(), List.of())),
                    profileMapper.toPrompts(prompts.getOrDefault(candidate.getId(), List.of())),
                    candidate.isPhotoVerified()));
        }
        return result;
    }

    /** Both sides must be open to the other's gender - the shelf is not exempt from preferences. */
    private boolean preferencesOverlap(User viewer, User candidate) {
        return wantedBy(viewer).contains(candidate.getGender())
                && wantedBy(candidate).contains(viewer.getGender());
    }

    /**
     * The genders a user wants to see. Someone who never set "Show me" gets the opposite
     * gender rather than everyone - a man with no preference is shown women, a woman men.
     */
    private static Set<Gender> wantedBy(User user) {
        if (user.getInterestedIn() != null && !user.getInterestedIn().isEmpty()) {
            return user.getInterestedIn();
        }
        if (user.getGender() == null) {
            return EnumSet.allOf(Gender.class);
        }
        return switch (user.getGender()) {
            case MAN -> EnumSet.of(Gender.WOMAN);
            case WOMAN -> EnumSet.of(Gender.MAN);
            default -> EnumSet.allOf(Gender.class);
        };
    }

    private Integer distanceLabel(User viewer, User candidate) {
        if (viewer.getLatitude() == null || candidate.getLatitude() == null) {
            return null;
        }
        return GeoUtils.displayDistanceKm(GeoUtils.distanceKm(
                viewer.getLatitude(), viewer.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude()));
    }

    private String reasonLabel(String reason) {
        return switch (reason) {
            case "POPULAR" -> "Popular this week";
            case "NEW" -> "New here";
            case "ACTIVE" -> "Active right now";
            default -> "Rising";
        };
    }
}
