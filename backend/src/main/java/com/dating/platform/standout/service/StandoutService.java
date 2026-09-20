package com.dating.platform.standout.service;

import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.interaction.service.LikeService;
import com.dating.platform.match.engine.CompatibilityScorer;
import com.dating.platform.match.service.MatchService;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.mapper.ProfileMapper;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.standout.dto.StandoutResponse;
import com.dating.platform.standout.entity.StandoutSnapshot;
import com.dating.platform.standout.repository.StandoutSnapshotRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import com.dating.platform.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final BlockService blockService;
    private final MatchService matchService;
    private final LikeService likeService;
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

        List<UUID> excluded = Stream.of(
                        blockService.hiddenUserIdsFor(viewerId).stream(),
                        matchService.matchedCounterpartIds(viewerId).stream(),
                        likeService.alreadyActedOn(viewerId).stream(),
                        Stream.of(viewerId))
                .flatMap(s -> s)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        // Over-fetch: some rows will be dropped by the gender-preference check below.
        List<StandoutSnapshot> snapshots = snapshotRepository.findTop(
                cycleKey, excluded, PageRequest.of(0, size * OVERFETCH_FACTOR));
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = snapshots.stream().map(StandoutSnapshot::getUserId).toList();
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
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
            if (candidate == null || candidateProfile == null || !preferencesOverlap(viewer, candidate)) {
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
        boolean viewerWants = viewer.getInterestedIn() == null || viewer.getInterestedIn().isEmpty()
                || viewer.getInterestedIn().contains(candidate.getGender());
        boolean candidateWants = candidate.getInterestedIn() == null || candidate.getInterestedIn().isEmpty()
                || candidate.getInterestedIn().contains(viewer.getGender());
        return viewerWants && candidateWants;
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
