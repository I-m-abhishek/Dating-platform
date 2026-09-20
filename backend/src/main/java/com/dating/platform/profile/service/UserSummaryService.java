package com.dating.platform.profile.service;

import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.user.dto.UserSummaryResponse;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import com.dating.platform.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds {@link UserSummaryResponse} cards in bulk.
 *
 * <p>Exists to kill the N+1 that every list screen would otherwise cause: one query for the
 * users, one for their photos, then an in-memory join. Callers pass the ids they already
 * have and get back a map keyed by user id.
 */
@Service
@RequiredArgsConstructor
public class UserSummaryService {

    private static final Duration RECENTLY_ACTIVE_WINDOW = Duration.ofHours(72);

    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;

    @Transactional(readOnly = true)
    public Map<UUID, UserSummaryResponse> summariesFor(List<UUID> userIds, User viewer) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userRepository.findAllById(userIds);
        Map<UUID, Photo> primaryPhotos = primaryPhotosOf(userIds);

        Map<UUID, UserSummaryResponse> result = new LinkedHashMap<>();
        for (User user : users) {
            result.put(user.getId(), toSummary(user, primaryPhotos.get(user.getId()), viewer, null));
        }
        return result;
    }

    /** Preserves the order of {@code userIds}, dropping ids that no longer resolve. */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> orderedSummaries(List<UUID> userIds, User viewer) {
        Map<UUID, UserSummaryResponse> byId = summariesFor(userIds, viewer);
        return userIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    public UserSummaryResponse toSummary(User user, Photo primaryPhoto, User viewer, Double compatibilityScore) {
        return new UserSummaryResponse(
                user.getId(),
                user.getDisplayName(),
                DateUtils.ageOf(user.getDateOfBirth()),
                user.getCity(),
                distanceBetween(viewer, user),
                primaryPhoto == null ? null : primaryPhoto.getUrl(),
                primaryPhoto == null ? null : primaryPhoto.getBlurhash(),
                user.isPhotoVerified(),
                isRecentlyActive(user),
                compatibilityScore);
    }

    private Map<UUID, Photo> primaryPhotosOf(List<UUID> userIds) {
        return photoRepository.findAllByUserIds(userIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getUser().getId(),
                        Function.identity(),
                        // the primary flag wins; otherwise the lowest display order
                        (a, b) -> {
                            if (a.isPrimaryPhoto() != b.isPrimaryPhoto()) {
                                return a.isPrimaryPhoto() ? a : b;
                            }
                            return Comparator.comparingInt(Photo::getDisplayOrder).compare(a, b) <= 0 ? a : b;
                        }));
    }

    private boolean isRecentlyActive(User user) {
        return user.getLastActiveAt() != null
                && Duration.between(user.getLastActiveAt(), Instant.now()).compareTo(RECENTLY_ACTIVE_WINDOW) < 0;
    }

    private Integer distanceBetween(User viewer, User target) {
        if (viewer == null || viewer.getLatitude() == null || target.getLatitude() == null) {
            return null;
        }
        return GeoUtils.displayDistanceKm(GeoUtils.distanceKm(
                viewer.getLatitude(), viewer.getLongitude(),
                target.getLatitude(), target.getLongitude()));
    }
}
