package com.dating.platform.auth.entity;

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
 * A refresh token, stored as a SHA-256 hash.
 *
 * <p>Access tokens are stateless and short lived; refresh tokens are the opposite - long
 * lived and revocable - which is why they are persisted. Storing the hash rather than the
 * value means a database leak does not hand an attacker working sessions.
 *
 * <p>Rotation: using a token immediately revokes it and issues a new one. If a revoked
 * token is presented again, the whole family is revoked, because that pattern means the
 * token was stolen and replayed.
 */
@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_family", columnList = "family_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseUuidEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** All tokens descended from one login share a family id. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 250)
    private String userAgent;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
