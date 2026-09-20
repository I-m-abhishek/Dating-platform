package com.dating.platform.standout.service;

import com.dating.platform.discovery.repository.DiscoveryRepository;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.standout.entity.StandoutSnapshot;
import com.dating.platform.standout.repository.StandoutSnapshotRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rebuilds the Standouts ranking.
 *
 * <p>Writes a whole new cycle, then deletes the previous one. Readers always see a complete,
 * consistent ranking - there is no window where the shelf is half-written, which a
 * delete-then-insert would create.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandoutRefreshService {

    private static final Duration POPULARITY_WINDOW = Duration.ofDays(7);
    private static final int MAX_SNAPSHOT_SIZE = 2000;
    private static final DateTimeFormatter CYCLE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DiscoveryRepository discoveryRepository;
    private final StandoutSnapshotRepository snapshotRepository;
    private final PopularityScorer popularityScorer;

    @Transactional
    public int refresh() {
        String cycleKey = CYCLE_FORMAT.format(Instant.now());
        Instant since = Instant.now().minus(POPULARITY_WINDOW);

        Map<UUID, LikeCounts> likeCounts = discoveryRepository.aggregateLikeCountsSince(since).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> new LikeCounts(((Number) row[1]).longValue(), ((Number) row[2]).longValue()),
                        (a, b) -> a));

        List<User> active = userRepository.findAllByStatus(UserStatus.ACTIVE);
        if (active.isEmpty()) {
            log.info("Standout refresh skipped - no active users");
            return 0;
        }

        Map<UUID, Profile> profiles = profileRepository
                .findAllByUserIdIn(active.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        List<Candidate> ranked = active.stream()
                .filter(u -> u.getOnboardingCompletedAt() != null && !u.isIncognito())
                .map(u -> {
                    LikeCounts counts = likeCounts.getOrDefault(u.getId(), LikeCounts.EMPTY);
                    PopularityScorer.Scored scored =
                            popularityScorer.score(u, profiles.get(u.getId()), counts.likes(), counts.superLikes());
                    return new Candidate(u, scored);
                })
                .filter(c -> c.scored().score() > 0)
                .sorted(Comparator.comparingDouble((Candidate c) -> c.scored().score()).reversed())
                .limit(MAX_SNAPSHOT_SIZE)
                .toList();

        List<StandoutSnapshot> snapshots = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            Candidate candidate = ranked.get(i);
            snapshots.add(StandoutSnapshot.builder()
                    .cycleKey(cycleKey)
                    .userId(candidate.user().getId())
                    .city(candidate.user().getCity())
                    .country(candidate.user().getCountry())
                    .score(candidate.scored().score())
                    .rankPosition(i + 1)
                    .reason(candidate.scored().reason())
                    .likesReceived(candidate.scored().likesReceived())
                    .build());
        }

        snapshotRepository.saveAll(snapshots);
        snapshotRepository.deleteAllByCycleKeyNot(cycleKey);

        log.info("Standout refresh complete: cycle {} with {} entries", cycleKey, snapshots.size());
        return snapshots.size();
    }

    private record LikeCounts(long likes, long superLikes) {
        static final LikeCounts EMPTY = new LikeCounts(0, 0);
    }

    private record Candidate(User user, PopularityScorer.Scored scored) {
    }
}
