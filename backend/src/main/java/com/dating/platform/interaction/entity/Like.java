package com.dating.platform.interaction.entity;

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
 * One person liking another, optionally anchored to a specific photo or prompt answer
 * with a short note - that context is what makes the Likes tab worth paying to see.
 *
 * <p>A reciprocal pair of likes is what creates a {@code Match}; the second like flips both
 * rows to {@link LikeStatus#MATCHED} inside one transaction.
 */
@Entity
@Table(name = "likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_likes_pair", columnNames = {"sender_id", "receiver_id"}),
        indexes = {
                @Index(name = "idx_likes_receiver_status", columnList = "receiver_id,status"),
                @Index(name = "idx_likes_sender_status", columnList = "sender_id,status"),
                @Index(name = "idx_likes_created", columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Like extends BaseUuidEntity {

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private LikeType type = LikeType.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LikeStatus status = LikeStatus.PENDING;

    /** Photo the sender reacted to, if they liked a specific one. */
    @Column(name = "target_photo_id")
    private UUID targetPhotoId;

    /** Prompt answer the sender reacted to, if any. */
    @Column(name = "target_prompt_answer_id")
    private UUID targetPromptAnswerId;

    /** Idempotency key from the client; unique per sender where present. */
    @Column(name = "client_like_id", length = 64)
    private String clientLikeId;

    @Column(name = "note", length = 200)
    private String note;

    /** True once the receiver has opened their Likes tab and seen this row. */
    @Column(name = "seen", nullable = false)
    @Builder.Default
    private boolean seen = false;

    /** Set when the like came from the auto-match engine rather than a swipe. */
    @Column(name = "from_auto_match", nullable = false)
    @Builder.Default
    private boolean fromAutoMatch = false;
}
