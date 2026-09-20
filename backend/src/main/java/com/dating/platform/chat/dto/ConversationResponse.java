package com.dating.platform.chat.dto;

import com.dating.platform.chat.entity.Conversation;
import com.dating.platform.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in the Messages list, and the header of an open thread.
 *
 * <p>{@code sendingState} is the whole opener rule expressed for the client: how many
 * messages are left before a reply is required, and why.
 */
@Schema(name = "ConversationResponse")
public record ConversationResponse(
        UUID id,
        UUID matchId,
        UserSummaryResponse participant,
        Conversation.ConversationStatus status,
        String lastMessagePreview,
        Instant lastMessageAt,
        UUID lastMessageSenderId,
        int unreadCount,
        boolean bothSpoke,
        SendingState sendingState,
        Instant createdAt
) {

    /**
     * @param canSend            whether the caller may send right now
     * @param remainingOpeners   messages left before a reply is required ({@code -1} = unrestricted)
     * @param reason             machine readable reason when {@code canSend} is false
     */
    @Schema(name = "SendingState")
    public record SendingState(boolean canSend, int remainingOpeners, int openerLimit, String reason, String message) {

        public static SendingState unrestricted() {
            return new SendingState(true, -1, -1, null, null);
        }
    }
}
