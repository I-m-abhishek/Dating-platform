package com.dating.platform.chat.controller;

import com.dating.platform.chat.dto.MessageResponse;
import com.dating.platform.chat.dto.SendMessageRequest;
import com.dating.platform.chat.service.ChatService;
import com.dating.platform.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * STOMP endpoints for chat.
 *
 * <p>Subscribe to {@code /topic/conversations/{id}} for messages, typing and read events.
 * Sending over the socket is offered for latency, but the REST endpoint does exactly the
 * same work - the client may use either.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    /** {@code /app/conversations/{id}/send} */
    @MessageMapping("/conversations/{conversationId}/send")
    public void send(@DestinationVariable UUID conversationId,
                     @Valid @Payload SendMessageRequest request,
                     Authentication authentication) {
        UUID senderId = principalOf(authentication).getId();
        MessageResponse response = chatService.send(senderId, conversationId, request);
        log.debug("Message {} sent over websocket", response.id());
    }

    /** {@code /app/conversations/{id}/typing} - ephemeral, never stored. */
    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId,
                       @Payload TypingPayload payload,
                       Authentication authentication) {
        UUID senderId = principalOf(authentication).getId();
        chatService.requireParticipant(senderId, conversationId);
        chatService.broadcastTyping(conversationId, senderId, payload != null && payload.typing());
    }

    /** {@code /app/conversations/{id}/read} */
    @MessageMapping("/conversations/{conversationId}/read")
    public void read(@DestinationVariable UUID conversationId, Authentication authentication) {
        chatService.markRead(principalOf(authentication).getId(), conversationId);
    }

    private UserPrincipal principalOf(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Unauthenticated STOMP frame reached a handler");
        }
        return principal;
    }

    public record TypingPayload(boolean typing) {
    }
}
