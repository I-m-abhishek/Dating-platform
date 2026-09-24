package com.dating.platform.subscription.service;

import com.dating.platform.common.exception.PremiumRequiredException;
import com.dating.platform.config.AppProperties;
import com.dating.platform.config.CacheConfig;
import com.dating.platform.subscription.dto.Entitlements;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.entity.PlanTier;
import com.dating.platform.subscription.entity.Subscription;
import com.dating.platform.subscription.entity.Subscription.SubscriptionStatus;
import com.dating.platform.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resolves what a user is allowed to do.
 *
 * <p>Every paywall in the codebase goes through {@link #require(UUID, Feature)} or reads a
 * numeric limit from {@link #entitlementsOf(UUID)}. No other class inspects
 * subscriptions directly - that is what keeps "what does Plus include" answerable in one place.
 *
 * <p>Results are cached briefly; any subscription write evicts the user's entry.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final AppProperties appProperties;

    /**
     * This bean through its proxy. The helpers below call {@link #entitlementsOf(UUID)}; a
     * plain {@code this} call would bypass the cache and query subscriptions every time.
     */
    @Lazy
    @Autowired
    private EntitlementService self;

    @Cacheable(cacheNames = CacheConfig.CACHE_ENTITLEMENTS, key = "#userId")
    @Transactional(readOnly = true)
    public Entitlements entitlementsOf(UUID userId) {
        List<Subscription> active =
                subscriptionRepository.findActiveForUser(userId, SubscriptionStatus.ACTIVE, Instant.now());

        Subscription best = active.stream()
                .max(Comparator.comparingInt(s -> s.getPlan().getTier().rank()))
                .orElse(null);

        PlanTier tier = best == null ? PlanTier.FREE : best.getPlan().getTier();
        String planName = best == null ? "Free" : best.getPlan().getName();
        Instant renewsAt = best == null ? null : best.getCurrentPeriodEnd();

        return Entitlements.from(tier, planName, renewsAt, limitsFor(tier));
    }

    public PlanTier tierOf(UUID userId) {
        return self.entitlementsOf(userId).tier();
    }

    public boolean has(UUID userId, Feature feature) {
        return self.entitlementsOf(userId).has(feature);
    }

    /** Throws {@link PremiumRequiredException} with the tier the client should upsell. */
    public void require(UUID userId, Feature feature) {
        if (!has(userId, feature)) {
            throw new PremiumRequiredException(feature.getLabel(), feature.getRequiredTier().name());
        }
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_ENTITLEMENTS, key = "#userId")
    public void evict(UUID userId) {
        // annotation does the work; the method exists so callers read clearly
    }

    private AppProperties.Quota.Tier limitsFor(PlanTier tier) {
        AppProperties.Quota quota = appProperties.quota();
        return switch (tier) {
            case FREE -> quota.free();
            case PLUS -> quota.plus();
            case PREMIUM -> quota.premium();
        };
    }
}
