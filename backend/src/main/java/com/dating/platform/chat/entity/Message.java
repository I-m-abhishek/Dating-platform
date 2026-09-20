package com.dating.platform.chat.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single chat message. Attachments hang off it, so a message can be text, media, or both.
 *
 * <p>Deleted messages are tombstoned rather than removed: the other participant has already
 * seen them, and a silently vanishing history is a moderation and trust problem.
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_conversation_created", columnList = "conversation_id,created_at"),
        @Index(name = "idx_messages_sender", columnList = "sender_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message extends BaseUuidEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Column(name = "body", length = 2000)
    private String body;

    /** Set when the message is a reply to another message. */
    @Column(name = "reply_to_id")
    private UUID replyToId;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /** Client generated id, echoed back so the sender can de-duplicate optimistic sends. */
    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MessageAttachment> attachments = new ArrayList<>();

    public void addAttachment(MessageAttachment attachment) {
        attachments.add(attachment);
        attachment.setMessage(this);
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        VOICE_NOTE,
        VIDEO,
        FILE,
        /** System notice: "You matched", "Call ended", "Message could not be delivered". */
        SYSTEM,
        CALL_SUMMARY
    }
}
