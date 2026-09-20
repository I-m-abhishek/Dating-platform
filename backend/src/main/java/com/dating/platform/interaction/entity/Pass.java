package com.dating.platform.interaction.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A skipped profile. Passes expire: after {@link #DEFAULT_COOLDOWN_DAYS} the profile can
 * come back into the feed, because tastes change and a permanently shrinking pool is the
 * fastest way to run a dating app out of inventory.
 */
@Entity
@Table(name = "passes",
        uniqueConstraints = @UniqueConstraint(name = "uk_passes_pair", columnNames = {"sender_id", "receiver_id"}),
        indexes = @Index(name = "idx_passes_sender_expires", columnList = "sender_id,expires_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pass extends BaseUuidEntity {

    public static final int DEFAULT_COOLDOWN_DAYS = 30;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
