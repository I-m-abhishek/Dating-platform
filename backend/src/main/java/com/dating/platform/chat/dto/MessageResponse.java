package com.dating.platform.chat.dto;

import com.dating.platform.chat.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "MessageResponse")
public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        boolean mine,
        Message.MessageType type,
        String body,
        List<AttachmentResponse> attachments,
        UUID replyToId,
        boolean deleted,
        Instant deliveredAt,
        Instant readAt,
        String clientMessageId,
        Instant createdAt
) {
}
