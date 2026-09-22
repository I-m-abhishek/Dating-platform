package com.dating.platform.chat.entity;

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

import java.time.Instant;
import java.util.UUID;

/**
 * The chat thread that exists for exactly one match.
 *
 * <p><b>Opener rule.</b> Until both people have spoken, the person who opened the
 * conversation may send only a limited number of messages ({@code app.chat.opener-message-limit}).
 * {@link #firstSenderId} and {@link #bothSpoke} are denormalised onto this row so the rule
 * can be enforced without counting messages on every send.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_match", columnNames = "match_id"),
        indexes = {
                @Index(name = "idx_conversations_user_a", columnList = "user_a_id,last_message_at"),
                @Index(name = "idx_conversations_user_b", columnList = "user_b_id,last_message_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends BaseUuidEntity {

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "first_sender_id")
    private UUID firstSenderId;

    /** True once both participants have sent at least one message - lifts the opener limit. */
    @Column(name = "both_spoke", nullable = false)
    @Builder.Default
    private boolean bothSpoke = false;

    @Column(name = "opener_message_count", nullable = false)
    @Builder.Default
    private int openerMessageCount = 0;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 160)
    private String lastMessagePreview;

    @Column(name = "last_message_sender_id")
    private UUID lastMessageSenderId;

    @Column(name = "unread_for_user_a", nullable = false)
    @Builder.Default
    private int unreadForUserA = 0;

    @Column(name = "unread_for_user_b", nullable = false)
    @Builder.Default
    private int unreadForUserB = 0;

    /**
     * How far each participant has read.
     *
     * <p>One updatable column per side, instead of a read flag on every message row.
     * Marking a long conversation read used to be an UPDATE over every unread message;
     * it is now a single timestamp write, and "has the peer seen this message" becomes a
     * comparison at read time.
     */
    @Column(name = "user_a_last_read_at")
    private Instant userALastReadAt;

    @Column(name = "user_b_last_read_at")
    private Instant userBLastReadAt;

    public Instant lastReadAtFor(UUID userId) {
        return userAId.equals(userId) ? userALastReadAt : userBLastReadAt;
    }

    public void markReadUpTo(UUID userId, Instant moment) {
        if (userAId.equals(userId)) {
            if (userALastReadAt == null || userALastReadAt.isBefore(moment)) {
                userALastReadAt = moment;
            }
        } else if (userBLastReadAt == null || userBLastReadAt.isBefore(moment)) {
            userBLastReadAt = moment;
        }
    }

    public UUID otherParticipant(UUID userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public boolean involves(UUID userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    public int unreadFor(UUID userId) {
        return userAId.equals(userId) ? unreadForUserA : unreadForUserB;
    }

    public void incrementUnreadFor(UUID userId) {
        if (userAId.equals(userId)) {
            unreadForUserA++;
        } else {
            unreadForUserB++;
        }
    }

    public void clearUnreadFor(UUID userId) {
        if (userAId.equals(userId)) {
            unreadForUserA = 0;
        } else {
            unreadForUserB = 0;
        }
    }

    public enum ConversationStatus {
        ACTIVE,
        /** One side unmatched or blocked - history stays readable, sending is refused. */
        CLOSED
    }
}
