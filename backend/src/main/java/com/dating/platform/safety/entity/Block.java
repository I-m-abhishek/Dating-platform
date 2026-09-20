package com.dating.platform.safety.entity;

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

import java.util.UUID;

/**
 * A one-directional block. Visibility rules treat it as symmetric: if either party
 * blocked the other, neither appears anywhere for the other.
 *
 * <p>Stored with raw ids rather than associations - block checks run on nearly every
 * read path and must not drag entity graphs along.
 */
@Entity
@Table(name = "blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_blocks_pair", columnNames = {"blocker_id", "blocked_id"}),
        indexes = {
                @Index(name = "idx_blocks_blocker", columnList = "blocker_id"),
                @Index(name = "idx_blocks_blocked", columnList = "blocked_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block extends BaseUuidEntity {

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "reason", length = 200)
    private String reason;
}
