package com.dating.platform.discovery.service;

import com.dating.platform.config.AppProperties;
import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.discovery.repository.DiscoveryRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns "who could this person plausibly meet" into a list of user ids.
 *
 * <p>Shared by the home feed and the auto-match engine so the two can never disagree about
 * eligibility - a profile you would never see while swiping must not arrive as your
 * weekly auto-match.
 *
 * <p>Hard filters (mutual gender preference, age range, blocks, prior interactions) run in
 * SQL. Soft ranking runs in the caller, on the returned pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateFinder {

    private final DiscoveryRepository discoveryRepository;
    private final AppProperties appProperties;

    /**
     * @param viewer       the user we are finding candidates for
     * @param filter       request-level overrides; {@code null} fields fall back to saved preferences
     * @param excludedIds  people already matched, liked, passed or auto-matched this period
     * @param poolSize     hard cap on rows returned
     */
    @Transactional(readOnly = true)
    public List<UUID> findCandidateIds(User viewer, FeedFilterRequest filter,
                                       Collection<UUID> excludedIds, int poolSize) {
        int minAge = firstNonNull(filter.minAge(), viewer.getPreferredMinAge(), 18);
        int maxAge = firstNonNull(filter.maxAge(), viewer.getPreferredMaxAge(), 99);
        int maxDistanceKm = firstNonNull(filter.maxDistanceKm(), viewer.getPreferredMaxDistanceKm(),
                appProperties.matching().maxDistanceKmDefault());

        // date_of_birth is between these two dates for someone whose age is in [minAge, maxAge]
        LocalDate today = LocalDate.now();
        LocalDate youngestDob = today.minusYears(minAge);
        LocalDate oldestDob = today.minusYears(maxAge + 1L).plusDays(1);

        Set<Gender> wanted = resolveWantedGenders(viewer, filter);
        boolean ignoreDistance = viewer.isGlobalMode();

        double latDelta = GeoUtils.latDelta(maxDistanceKm);
        double lonDelta = GeoUtils.lonDelta(maxDistanceKm, viewer.getLatitude() == null ? 0 : viewer.getLatitude());
        double lat = viewer.getLatitude() == null ? 0 : viewer.getLatitude();
        double lon = viewer.getLongitude() == null ? 0 : viewer.getLongitude();

        // The IN / NOT IN clauses need a non-empty collection; the viewer is always excluded anyway.
        Collection<UUID> excluded = excludedIds == null || excludedIds.isEmpty()
                ? List.of(viewer.getId())
                : excludedIds;

        List<UUID> ids = discoveryRepository.findCandidateIds(
                viewer.getId(),
                viewer.getGender().name(),
                wanted.stream().map(Enum::name).collect(Collectors.toSet()),
                oldestDob,
                youngestDob,
                ignoreDistance,
                lat - latDelta,
                lat + latDelta,
                lon - lonDelta,
                lon + lonDelta,
                excluded,
                poolSize);

        log.debug("Candidate pool for {}: {} ids (age {}-{}, {} km, genders {})",
                viewer.getId(), ids.size(), minAge, maxAge, ignoreDistance ? "global" : maxDistanceKm, wanted);
        return ids;
    }

    /**
     * Distance is filtered as a box in SQL; this is the exact circular test the box approximates.
     */
    public boolean withinDistance(User viewer, User candidate, int maxDistanceKm) {
        if (viewer.isGlobalMode()) {
            return true;
        }
        if (viewer.getLatitude() == null || candidate.getLatitude() == null) {
            return false;
        }
        double km = GeoUtils.distanceKm(viewer.getLatitude(), viewer.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude());
        return km <= maxDistanceKm;
    }

    private Set<Gender> resolveWantedGenders(User viewer, FeedFilterRequest filter) {
        if (filter.genders() != null && !filter.genders().isEmpty()) {
            return filter.genders();
        }
        if (viewer.getInterestedIn() != null && !viewer.getInterestedIn().isEmpty()) {
            return viewer.getInterestedIn();
        }
        return Arrays.stream(Gender.values()).collect(Collectors.toSet());
    }

    private int firstNonNull(Integer a, Integer b, int fallback) {
        if (a != null) {
            return a;
        }
        return b != null ? b : fallback;
    }
}
