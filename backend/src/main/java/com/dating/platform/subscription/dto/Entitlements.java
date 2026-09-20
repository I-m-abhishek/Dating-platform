package com.dating.platform.subscription.dto;

import com.dating.platform.config.AppProperties;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.entity.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * The single object the client needs to render every paywall correctly.
 *
 * <p>Limits use {@code -1} for unlimited. The frontend must never hardcode these numbers;
 * it reads them from {@code GET /api/v1/subscriptions/me/entitlements}.
 */
@Schema(name = "Entitlements")
public record Entitlements(
        PlanTier tier,
        String planName,
        Instant renewsAt,
        int photoCommentsPerDay,
        int likesPerDay,
        int autoMatchPerWeek,
        int autoMatchPerDay,
        int rewindsPerDay,
        List<Feature> unlockedFeatures,
        List<Feature> lockedFeatures
) {

    public static Entitlements from(PlanTier tier, String planName, Instant renewsAt, AppProperties.Quota.Tier limits) {
        List<Feature> unlocked = Arrays.stream(Feature.values())
                .filter(f -> tier.atLeast(f.getRequiredTier()))
                .toList();
        List<Feature> locked = Arrays.stream(Feature.values())
                .filter(f -> !tier.atLeast(f.getRequiredTier()))
                .toList();
        return new Entitlements(
                tier,
                planName,
                renewsAt,
                limits.photoCommentsPerDay(),
                limits.likesPerDay(),
                limits.autoMatchPerWeek(),
                limits.autoMatchPerDay(),
                limits.rewindsPerDay(),
                unlocked,
                locked);
    }

    public boolean has(Feature feature) {
        return tier.atLeast(feature.getRequiredTier());
    }

    public static boolean isUnlimited(int limit) {
        return limit < 0;
    }
}
