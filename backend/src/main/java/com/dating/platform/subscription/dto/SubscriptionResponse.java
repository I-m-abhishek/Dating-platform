package com.dating.platform.subscription.dto;

import com.dating.platform.subscription.entity.PlanTier;
import com.dating.platform.subscription.entity.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "SubscriptionResponse")
public record SubscriptionResponse(
        UUID id,
        String planCode,
        String planName,
        PlanTier tier,
        Subscription.SubscriptionStatus status,
        Instant startedAt,
        Instant currentPeriodEnd,
        boolean autoRenew
) {

    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getId(),
                s.getPlan().getCode(),
                s.getPlan().getName(),
                s.getPlan().getTier(),
                s.getStatus(),
                s.getStartedAt(),
                s.getCurrentPeriodEnd(),
                s.isAutoRenew());
    }
}
