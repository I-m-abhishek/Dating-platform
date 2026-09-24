package com.dating.platform.discovery.service;

import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.user.entity.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * The per-candidate filters that run after the SQL candidate query: everything that lives
 * on the profile rather than the user row, plus activity.
 *
 * <p>A filter the candidate has not answered excludes them. Someone who filters for
 * non-smokers is asking not to see smokers, and an unanswered profile cannot promise that.
 */
public final class FeedFilterMatcher {

    private FeedFilterMatcher() {
    }

    public static boolean matches(User user, Profile profile, FeedFilterRequest filter, Instant now) {
        if (!anyOf(filter.intents(), profile.getRelationshipIntent())) {
            return false;
        }
        if (filter.minHeightCm() != null
                && (profile.getHeightCm() == null || profile.getHeightCm() < filter.minHeightCm())) {
            return false;
        }
        if (filter.maxHeightCm() != null
                && (profile.getHeightCm() == null || profile.getHeightCm() > filter.maxHeightCm())) {
            return false;
        }
        if (Boolean.TRUE.equals(filter.onlyVerified()) && !user.isPhotoVerified()) {
            return false;
        }
        if (filter.activeWithinHours() != null
                && (user.getLastActiveAt() == null
                || user.getLastActiveAt().isBefore(now.minus(Duration.ofHours(filter.activeWithinHours()))))) {
            return false;
        }
        if (!anyOf(filter.children(), profile.getChildren())
                || !anyOf(filter.drinking(), profile.getDrinking())
                || !anyOf(filter.smoking(), profile.getSmoking())) {
            return false;
        }
        if (filter.interestIds() != null && !filter.interestIds().isEmpty()) {
            return profile.getInterests() != null && profile.getInterests().stream()
                    .anyMatch(i -> filter.interestIds().contains(i.getId()));
        }
        return true;
    }

    /** An empty or absent filter accepts anyone; otherwise the value must be one of those chosen. */
    private static <T> boolean anyOf(Collection<T> wanted, T actual) {
        return wanted == null || wanted.isEmpty() || (actual != null && wanted.contains(actual));
    }
}
