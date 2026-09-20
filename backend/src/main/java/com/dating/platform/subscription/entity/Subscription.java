package com.dating.platform.subscription.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's subscription to a {@link Plan}.
 *
 * <p>The payment provider is deliberately abstracted to a reference string: this project
 * ships without a real processor, and swapping in Stripe or the app stores means writing
 * a webhook that flips {@link #status} - nothing else changes.
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_user", columnList = "user_id"),
        @Index(name = "idx_subscriptions_user_status", columnList = "user_id,status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseUuidEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscriptions_plan"))
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private boolean autoRenew = true;

    @Column(name = "provider", length = 30)
    @Builder.Default
    private String provider = "internal";

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE && currentPeriodEnd.isAfter(Instant.now());
    }

    public enum SubscriptionStatus {
        ACTIVE,
        PAST_DUE,
        CANCELLED,
        EXPIRED
    }
}
