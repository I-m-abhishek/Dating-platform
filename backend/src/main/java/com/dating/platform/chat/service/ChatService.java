package com.dating.platform.chat.service;

import com.dating.platform.chat.dto.AttachmentResponse;
import com.dating.platform.chat.dto.ConversationResponse;
import com.dating.platform.chat.dto.MessageResponse;
import com.dating.platform.chat.dto.SendMessageRequest;
import com.dating.platform.chat.entity.Conversation;
import com.dating.platform.chat.entity.Message;
import com.dating.platform.chat.entity.MessageAttachment;
import com.dating.platform.chat.repository.ConversationRepository;
import com.dating.platform.chat.repository.MessageRepository;
import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ForbiddenException;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.common.response.CursorPageResponse;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.config.AppProperties;
import com.dating.platform.match.service.MatchService;
import com.dating.platform.media.entity.MediaAsset;
import com.dating.platform.media.service.MediaAssetService;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.service.NotificationService;
import com.dating.platform.profile.service.UserSummaryService;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.user.dto.UserSummaryResponse;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversations and messages.
 *
 * <p><b>The opener rule.</b> When a match is new, the person who speaks first may send at
 * most {@code app.chat.opener-message-limit} messages until the other side replies. This
 * exists to stop one-sided message storms, which are the single biggest reason people leave
 * dating apps. It is enforced here, in the write path - the client only renders what the
 * server already decided in {@code sendingState}.
 *
 * <p>Delivery: messages are persisted first, then pushed over STOMP. A dropped socket costs
 * a live update, never a message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final String TOPIC_CONVERSATION = "/topic/conversations/";
    private static final int PREVIEW_LENGTH = 160;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final MediaAssetService mediaAssetService;
    private final BlockService blockService;
    private final UserSummaryService userSummaryService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AppProperties appProperties;

    // ---- reading -------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listConversations(UUID userId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findAllForUser(
                userId, Conversation.ConversationStatus.ACTIVE, pageable);
        return PageResponse.of(toConversationResponses(userId, page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID userId, UUID conversationId) {
        Conversation conversation = requireParticipant(userId, conversationId);
        return toConversationResponses(userId, List.of(conversation)).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    /**
     * Newest-first page of history.
     *
     * <p>The cursor is {@code "<instant>_<uuid>"} - the timestamp AND id of the oldest
     * message the client already holds. A timestamp alone is not a unique position: two
     * messages sharing a millisecond straddle the page boundary, and one of them is either
     * repeated on the next page or lost between the two. A plain ISO timestamp is still
     * accepted so an older client keeps paging, it just keeps the old tie behaviour.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> messages(UUID userId, UUID conversationId,
                                                        String before, int limit) {
        Conversation conversation = requireParticipant(userId, conversationId);
        int size = Math.clamp(limit, 1, 100);

        Cursor cursor = Cursor.parse(before);
        List<Message> messages = messageRepository.findPage(
                conversationId, cursor.at(), cursor.id(), PageRequest.of(0, size + 1));

        boolean hasMore = messages.size() > size;
        List<Message> pageContent = hasMore ? messages.subList(0, size) : messages;

        Instant peerReadAt = conversation.lastReadAtFor(conversation.otherParticipant(userId));

        List<MessageResponse> rows = pageContent.stream()
                .map(m -> toResponse(m, userId, peerReadAt))
                .toList();

        String nextCursor = null;
        if (hasMore && !pageContent.isEmpty()) {
            Message last = pageContent.get(pageContent.size() - 1);
            nextCursor = last.getCreatedAt() + "_" + last.getId();
        }

        return CursorPageResponse.of(rows, nextCursor);
    }

    /**
     * A position in a conversation: when, and which message.
     *
     * <p>{@link #id()} falls back to the maximum UUID so that a bare timestamp cursor keeps
     * the old "everything strictly before this instant" meaning - every real id sorts below
     * it, so nothing is skipped.
     */
    private record Cursor(Instant at, UUID id) {

        private static final UUID MAX_UUID = new UUID(-1L, -1L);

        static Cursor parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new Cursor(Instant.now().plus(1, ChronoUnit.DAYS), MAX_UUID);
            }
            int split = raw.lastIndexOf('_');
            if (split < 0) {
                return new Cursor(Instant.parse(raw), MAX_UUID);
            }
            return new Cursor(
                    Instant.parse(raw.substring(0, split)),
                    UUID.fromString(raw.substring(split + 1)));
        }
    }

    @Transactional(readOnly = true)
    public long totalUnread(UUID userId) {
        return conversationRepository.totalUnreadFor(userId);
    }

    // ---- writing -------------------------------------------------------

    @Transactional
    public MessageResponse send(UUID senderId, UUID conversationId, SendMessageRequest request) {
        Conversation conversation = requireParticipant(senderId, conversationId);
        UUID recipientId = conversation.otherParticipant(senderId);

        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            throw new ForbiddenException(ErrorCode.CONVERSATION_CLOSED,
                    ErrorCode.CONVERSATION_CLOSED.getDefaultMessage());
        }
        blockService.assertNotBlocked(senderId, recipientId);

        // Idempotency: a retried send with the same client id returns the original message.
        if (request.clientMessageId() != null) {
            Optional<Message> duplicate = messageRepository
                    .findFirstByClientMessageIdAndSenderId(request.clientMessageId(), senderId);
            if (duplicate.isPresent()) {
                return toResponse(duplicate.get(), senderId);
            }
        }

        enforceOpenerLimit(conversation, senderId);

        int maxLength = appProperties.chat().maxMessageLength();
        if (request.body() != null && request.body().length() > maxLength) {
            throw new BusinessException(ErrorCode.MESSAGE_TOO_LONG,
                    "Messages are limited to " + maxLength + " characters");
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(request.typeOrDefault())
                .body(request.body() == null ? null : request.body().trim())
                .replyToId(request.replyToId())
                .clientMessageId(request.clientMessageId())
                .deliveredAt(Instant.now())
                .build();

        for (MediaAsset asset : mediaAssetService.consume(senderId, request.attachmentIds())) {
            message.addAttachment(MessageAttachment.builder()
                    .storageKey(asset.getStorageKey())
                    .url(asset.getUrl())
                    .contentType(asset.getContentType())
                    .fileName(asset.getFileName())
                    .sizeBytes(asset.getSizeBytes())
                    .width(asset.getWidth())
                    .height(asset.getHeight())
                    .durationSeconds(asset.getDurationSeconds())
                    .waveform(asset.getWaveform())
                    .build());
        }

        Message saved = messageRepository.save(message);
        applyConversationSideEffects(conversation, senderId, recipientId, saved);
        matchService.touchInteraction(conversation.getMatchId());

        MessageResponse response = toResponse(saved, senderId);
        broadcast(conversationId, response);
        notificationService.notifyAsync(recipientId, NotificationType.NEW_MESSAGE,
                "New message", previewOf(saved), senderId, "CONVERSATION", conversationId, null);

        return response;
    }

    /**
     * The opener limit.
     *
     * <p>Until both people have spoken, only the opener's count matters. Once the other
     * person replies, {@code bothSpoke} flips permanently and the limit never applies again
     * to this conversation.
     */
    private void enforceOpenerLimit(Conversation conversation, UUID senderId) {
        if (conversation.isBothSpoke()) {
            return;
        }
        if (conversation.getFirstSenderId() == null || !conversation.getFirstSenderId().equals(senderId)) {
            return; // first message overall, or the awaited reply
        }
        int limit = appProperties.chat().openerMessageLimit();
        if (conversation.getOpenerMessageCount() >= limit) {
            throw new ForbiddenException(ErrorCode.OPENER_LIMIT_REACHED,
                    "You can send " + limit + " messages before they reply");
        }
    }

    private void applyConversationSideEffects(Conversation conversation, UUID senderId,
                                              UUID recipientId, Message saved) {
        if (conversation.getFirstSenderId() == null) {
            conversation.setFirstSenderId(senderId);
            conversation.setOpenerMessageCount(1);
        } else if (!conversation.isBothSpoke() && conversation.getFirstSenderId().equals(senderId)) {
            conversation.setOpenerMessageCount(conversation.getOpenerMessageCount() + 1);
        } else if (!conversation.isBothSpoke()) {
            conversation.setBothSpoke(true);
        }

        conversation.setLastMessageAt(saved.getCreatedAt() == null ? Instant.now() : saved.getCreatedAt());
        conversation.setLastMessagePreview(previewOf(saved));
        conversation.setLastMessageSenderId(senderId);
        conversation.incrementUnreadFor(recipientId);
        conversationRepository.save(conversation);
    }

    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        Conversation conversation = requireParticipant(userId, conversationId);

        /*
         * One watermark, not a write per message. The old version UPDATEd read_at on every
         * unread row, so opening a thread with 500 unread messages was 500 row writes to
         * record a single fact: how far this person has read.
         */
        messageRepository
                .findFirstByConversationIdAndSenderIdNotOrderByCreatedAtDesc(conversationId, userId)
                .ifPresent(newest -> conversation.markReadUpTo(userId, newest.getCreatedAt()));

        conversation.clearUnreadFor(userId);
        conversationRepository.save(conversation);

        messagingTemplate.convertAndSend(TOPIC_CONVERSATION + conversationId,
                Map.of("event", "READ", "conversationId", conversationId, "readerId", userId));
    }

    @Transactional
    public void deleteMessage(UUID userId, UUID conversationId, UUID messageId) {
        requireParticipant(userId, conversationId);
        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException("You can only delete your own messages");
        }
        message.setDeleted(true);
        message.setBody(null);
        message.getAttachments().clear();
        messageRepository.save(message);

        messagingTemplate.convertAndSend(TOPIC_CONVERSATION + conversationId,
                Map.of("event", "DELETED", "conversationId", conversationId, "messageId", messageId));
    }

    /** Ephemeral, never persisted. */
    public void broadcastTyping(UUID conversationId, UUID senderId, boolean typing) {
        messagingTemplate.convertAndSend(TOPIC_CONVERSATION + conversationId,
                Map.of("event", "TYPING", "conversationId", conversationId,
                        "userId", senderId, "typing", typing));
    }

    /** Used by the call module to drop a call summary into the thread. */
    @Transactional
    public void appendSystemMessage(UUID conversationId, UUID senderId,
                                    Message.MessageType type, String body) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        Message saved = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(type)
                .body(body)
                .deliveredAt(Instant.now())
                .build());
        conversation.setLastMessageAt(Instant.now());
        conversation.setLastMessagePreview(body);
        conversationRepository.save(conversation);
        broadcast(conversationId, toResponse(saved, senderId));
    }

    // ---- helpers -------------------------------------------------------

    @Transactional(readOnly = true)
    public Conversation requireParticipant(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        if (!conversation.involves(userId)) {
            throw new ForbiddenException("This conversation does not belong to you");
        }
        return conversation;
    }

    private void broadcast(UUID conversationId, MessageResponse message) {
        try {
            messagingTemplate.convertAndSend(TOPIC_CONVERSATION + conversationId, message);
        } catch (Exception e) {
            log.debug("Live broadcast for conversation {} failed: {}", conversationId, e.getMessage());
        }
    }

    private List<ConversationResponse> toConversationResponses(UUID viewerId, List<Conversation> conversations) {
        if (conversations.isEmpty()) {
            return List.of();
        }
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", viewerId));

        List<UUID> otherIds = conversations.stream().map(c -> c.otherParticipant(viewerId)).toList();
        Map<UUID, UserSummaryResponse> summaries = userSummaryService.summariesFor(otherIds, viewer);

        List<ConversationResponse> responses = new ArrayList<>(conversations.size());
        for (Conversation conversation : conversations) {
            UserSummaryResponse participant = summaries.get(conversation.otherParticipant(viewerId));
            if (participant == null) {
                continue;
            }
            responses.add(new ConversationResponse(
                    conversation.getId(),
                    conversation.getMatchId(),
                    participant,
                    conversation.getStatus(),
                    conversation.getLastMessagePreview(),
                    conversation.getLastMessageAt(),
                    conversation.getLastMessageSenderId(),
                    conversation.unreadFor(viewerId),
                    conversation.isBothSpoke(),
                    sendingStateFor(conversation, viewerId),
                    conversation.getCreatedAt()));
        }
        return responses;
    }

    /** Turns the opener rule into something the UI can render without re-deriving it. */
    private ConversationResponse.SendingState sendingStateFor(Conversation conversation, UUID viewerId) {
        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            return new ConversationResponse.SendingState(false, 0, 0, "CONVERSATION_CLOSED",
                    "This conversation is no longer available");
        }
        if (conversation.isBothSpoke()) {
            return ConversationResponse.SendingState.unrestricted();
        }

        int limit = appProperties.chat().openerMessageLimit();
        boolean viewerOpened = viewerId.equals(conversation.getFirstSenderId());
        if (!viewerOpened) {
            // Either nobody has spoken, or the other person did and it is our turn.
            return ConversationResponse.SendingState.unrestricted();
        }

        int remaining = Math.max(0, limit - conversation.getOpenerMessageCount());
        if (remaining == 0) {
            return new ConversationResponse.SendingState(false, 0, limit, "OPENER_LIMIT_REACHED",
                    "Wait for a reply before sending more messages");
        }
        return new ConversationResponse.SendingState(true, remaining, limit, null,
                remaining + " more until they reply");
    }

    private MessageResponse toResponse(Message message, UUID viewerId) {
        return toResponse(message, viewerId, null);
    }

    /**
     * @param peerReadAt how far the OTHER participant has read, or null when unknown
     */
    private MessageResponse toResponse(Message message, UUID viewerId, Instant peerReadAt) {
        List<AttachmentResponse> attachments = message.getAttachments() == null ? List.of()
                : message.getAttachments().stream().map(AttachmentResponse::from).toList();

        /*
         * Read state is derived, not stored per row. A message I sent counts as read once
         * the other person's watermark has reached it. message.readAt is still consulted as
         * a fallback so conversations written before the watermark existed keep their
         * receipts.
         */
        Instant readAt = message.getReadAt();
        boolean mine = message.getSenderId().equals(viewerId);
        if (mine && peerReadAt != null && !peerReadAt.isBefore(message.getCreatedAt())) {
            readAt = peerReadAt;
        }

        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getSenderId().equals(viewerId),
                message.getType(),
                message.isDeleted() ? null : message.getBody(),
                message.isDeleted() ? Collections.emptyList() : attachments,
                message.getReplyToId(),
                message.isDeleted(),
                message.getDeliveredAt(),
                readAt,
                message.getClientMessageId(),
                message.getCreatedAt());
    }

    private String previewOf(Message message) {
        if (message.getBody() != null && !message.getBody().isBlank()) {
            String body = message.getBody().trim();
            return body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) : body;
        }
        return switch (message.getType()) {
            case IMAGE -> "Sent a photo";
            case VOICE_NOTE -> "Sent a voice note";
            case VIDEO -> "Sent a video";
            case FILE -> "Sent a file";
            case CALL_SUMMARY -> "Call";
            default -> "New message";
        };
    }
}
