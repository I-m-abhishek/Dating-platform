package com.dating.platform.chat.dto;

import com.dating.platform.chat.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(name = "SendMessageRequest")
public record SendMessageRequest(

        @Size(max = 2000, message = "Messages are limited to 2000 characters")
        String body,

        Message.MessageType type,

        /** Attachment ids returned by the upload endpoint. */
        @Size(max = 6, message = "At most 6 attachments per message")
        List<UUID> attachmentIds,

        UUID replyToId,

        /**
         * Client generated id. Echoed back and used for de-duplication, so a retried send
         * over a flaky connection cannot post the same message twice.
         */
        @Size(max = 64)
        String clientMessageId
) {

    @AssertTrue(message = "Write something or add an attachment")
    public boolean hasContent() {
        return (body != null && !body.isBlank()) || (attachmentIds != null && !attachmentIds.isEmpty());
    }

    public Message.MessageType typeOrDefault() {
        if (type != null) {
            return type;
        }
        return attachmentIds == null || attachmentIds.isEmpty()
                ? Message.MessageType.TEXT : Message.MessageType.IMAGE;
    }
}
