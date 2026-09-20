package com.dating.platform.subscription.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.config.CacheConfig;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.service.NotificationService;
import com.dating.platform.subscription.dto.Entitlements;
import com.dating.platform.subscription.dto.PlanResponse;
import com.dating.platform.subscription.dto.SubscribeRequest;
import com.dating.platform.subscription.dto.SubscriptionResponse;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.entity.Plan;
import com.dating.platform.subscription.entity.Subscription;
import com.dating.platform.subscription.entity.Subscription.SubscriptionStatus;
import com.dating.platform.subscription.repository.PlanRepository;
import com.dating.platform.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plans, purchases and cancellation.
 *
 * <p><b>Payments are intentionally stubbed.</b> {@code paymentToken} is recorded but not
 * verified, and the subscription is granted immediately. Everything downstream reads
 * entitlements rather than payment state, so wiring a real processor means adding a webhook
 * that flips {@link Subscription#getStatus()} - no other module changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementService entitlementService;
    private final NotificationService notificationService;

    @Cacheable(CacheConfig.CACHE_PLANS)
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Entitlements entitlements(UUID userId) {
        return entitlementService.entitlementsOf(userId);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> current(UUID userId) {
        return subscriptionRepository.findActiveForUser(userId, SubscriptionStatus.ACTIVE, Instant.now())
                .stream()
                .findFirst()
                .map(SubscriptionResponse::from);
    }

    @Transactional
    public SubscriptionResponse subscribe(UUID userId, SubscribeRequest request) {
        Plan plan = planRepository.findByCode(request.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        boolean alreadyOnSameOrBetter = subscriptionRepository
                .findActiveForUser(userId, SubscriptionStatus.ACTIVE, Instant.now()).stream()
                .anyMatch(s -> s.getPlan().getTier().atLeast(plan.getTier()));
        if (alreadyOnSameOrBetter) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_ACTIVE,
                    "You already have this plan or a better one");
        }

        Instant now = Instant.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .currentPeriodEnd(now.plus(plan.getBillingPeriodMonths() * 30L, ChronoUnit.DAYS))
                .autoRenew(true)
                .provider("internal")
                .providerReference(request.paymentToken())
                .build());

        entitlementService.evict(userId);
        notificationService.notifyAsync(userId, NotificationType.SUBSCRIPTION_UPDATE,
                plan.getName() + " is active", "Your new features are ready to use",
                null, "SUBSCRIPTION", subscription.getId(), null);

        log.info("User {} subscribed to {}", userId, plan.getCode());
        return SubscriptionResponse.from(subscription);
    }

    /**
     * Cancels auto-renewal. Access continues until the end of the paid period - cutting it
     * off immediately would be taking money for nothing.
     */
    @Transactional
    public SubscriptionResponse cancel(UUID userId) {
        Subscription subscription = subscriptionRepository
                .findActiveForUser(userId, SubscriptionStatus.ACTIVE, Instant.now()).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("You do not have an active subscription"));

        subscription.setAutoRenew(false);
        subscription.setCancelledAt(Instant.now());
        subscriptionRepository.save(subscription);
        entitlementService.evict(userId);

        return SubscriptionResponse.from(subscription);
    }

    /** Safety net in case a renewal webhook never arrives. */
    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    @Transactional
    public void expireLapsedSubscriptions() {
        int expired = subscriptionRepository.expireLapsed(
                Instant.now(), SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED);
        if (expired > 0) {
            log.info("Expired {} lapsed subscriptions", expired);
        }
    }

    private PlanResponse toPlanResponse(Plan plan) {
        List<Feature> features = Arrays.stream(Feature.values())
                .filter(f -> plan.getTier().atLeast(f.getRequiredTier()))
                .toList();
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getTier(),
                plan.priceMajor(),
                plan.getCurrency(),
                plan.getBillingPeriodMonths(),
                features.stream().map(Feature::getLabel).toList(),
                features);
    }
}
