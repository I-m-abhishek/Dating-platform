package com.dating.platform.quota.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One counter per (user, feature, period).
 *
 * <p>{@code periodKey} is a date for daily features ({@code 2026-09-19}) or an ISO week
 * for weekly ones ({@code 2026-W38}). Storing the key rather than a timestamp makes the
 * reset boundary explicit and lets old rows be pruned by a simple range delete.
 */
@Entity
@Table(name = "usage_counters",
        uniqueConstraints = @UniqueConstraint(name = "uk_usage_counters_scope",
                columnNames = {"user_id", "feature", "period_key"}),
        indexes = @Index(name = "idx_usage_counters_user", columnList = "user_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageCounter extends BaseUuidEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 40)
    private QuotaFeature feature;

    @Column(name = "period_key", nullable = false, length = 12)
    private String periodKey;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private int used = 0;
}
