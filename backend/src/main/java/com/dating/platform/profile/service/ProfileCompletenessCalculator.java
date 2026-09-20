package com.dating.platform.profile.service;

import com.dating.platform.profile.entity.Profile;
import com.dating.platform.user.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns "how finished is this profile" into a single 0..1 number.
 *
 * <p>Used in three places, which is why it is its own class:
 * the onboarding nudge, the discovery ranking boost, and the auto-match eligibility gate.
 * Weights are intentionally biased towards the things that actually drive conversations
 * (photos and prompts) rather than the things that are easy to fill in.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProfileCompletenessCalculator {

    private static final Map<String, Double> WEIGHTS = new LinkedHashMap<>();

    static {
        WEIGHTS.put("photos", 0.30);
        WEIGHTS.put("prompts", 0.20);
        WEIGHTS.put("bio", 0.12);
        WEIGHTS.put("interests", 0.12);
        WEIGHTS.put("qualities", 0.10);
        WEIGHTS.put("intent", 0.06);
        WEIGHTS.put("basics", 0.10);
    }

    private static final int TARGET_PHOTOS = 4;
    private static final int TARGET_PROMPTS = 3;
    private static final int TARGET_INTERESTS = 5;
    private static final int TARGET_QUALITIES = 3;

    public static double calculate(User user, Profile profile, long photoCount, long promptCount) {
        double score = 0d;
        score += WEIGHTS.get("photos") * ratio(photoCount, TARGET_PHOTOS);
        score += WEIGHTS.get("prompts") * ratio(promptCount, TARGET_PROMPTS);
        score += WEIGHTS.get("bio") * (hasText(profile.getBio()) ? 1 : 0);
        score += WEIGHTS.get("interests") * ratio(size(profile.getInterests()), TARGET_INTERESTS);
        score += WEIGHTS.get("qualities") * ratio(size(profile.getQualities()), TARGET_QUALITIES);
        score += WEIGHTS.get("intent") * (profile.getRelationshipIntent() != null ? 1 : 0);
        score += WEIGHTS.get("basics") * basicsRatio(user, profile);
        return Math.round(Math.min(1d, score) * 1000d) / 1000d;
    }

    private static double basicsRatio(User user, Profile profile) {
        int filled = 0;
        int total = 5;
        if (profile.getJobTitle() != null || profile.getSchool() != null) {
            filled++;
        }
        if (profile.getHeightCm() != null) {
            filled++;
        }
        if (profile.getChildren() != null) {
            filled++;
        }
        if (user.getCity() != null) {
            filled++;
        }
        if (profile.getLanguages() != null && !profile.getLanguages().isEmpty()) {
            filled++;
        }
        return (double) filled / total;
    }

    private static double ratio(long actual, int target) {
        return Math.min(1d, (double) actual / target);
    }

    private static int size(java.util.Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
