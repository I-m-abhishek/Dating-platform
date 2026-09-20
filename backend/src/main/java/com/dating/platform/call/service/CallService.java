package com.dating.platform.call.service;

import com.dating.platform.call.dto.CallResponse;
import com.dating.platform.call.dto.SignalMessage;
import com.dating.platform.call.entity.CallSession;
import com.dating.platform.call.entity.CallSession.CallStatus;
import com.dating.platform.call.repository.CallSessionRepository;
import com.dating.platform.chat.entity.Conversation;
import com.dating.platform.chat.entity.Message;
import com.dating.platform.chat.service.ChatService;
import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ForbiddenException;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.service.NotificationService;
import com.dating.platform.safety.service.BlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Call lifecycle and signalling relay.
 *
 * <p>Calls are only possible inside an active conversation, which means only between people
 * who have matched. That single check is what stops calling from becoming a harassment
 * vector, so it lives at the top of every entry point here rather than in a controller.
 *
 * <p>Signalling frames are relayed to the other party's private queue after verifying
 * membership; the payload is never inspected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    public static final String USER_CALL_QUEUE = "/queue/calls";
    private static final List<CallStatus> LIVE = List.of(CallStatus.RINGING, CallStatus.ACTIVE);
    private static final Duration RING_TIMEOUT = Duration.ofSeconds(45);

    private final CallSessionRepository callRepository;
    private final ChatService chatService;
    private final BlockService blockService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final IceServerProvider iceServerProvider;

    @Transactional
    public CallResponse start(UUID callerId, UUID conversationId, CallSession.CallType type) {
        Conversation conversation = chatService.requireParticipant(callerId, conversationId);
        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            throw new ForbiddenException(ErrorCode.CALL_NOT_ALLOWED, "You can only call an active match");
        }
        UUID calleeId = conversation.otherParticipant(callerId);
        blockService.assertNotBlocked(callerId, calleeId);

        if (!callRepository.findLive(conversationId, LIVE).isEmpty()) {
            throw new BusinessException(ErrorCode.CALL_ALREADY_ACTIVE);
        }
        if (!callRepository.findLiveForUser(calleeId, LIVE).isEmpty()) {
            throw new BusinessException(ErrorCode.CALL_ALREADY_ACTIVE, "They are already on a call");
        }

        CallSession session = callRepository.save(CallSession.builder()
                .conversationId(conversationId)
                .callerId(callerId)
                .calleeId(calleeId)
                .type(type)
                .status(CallStatus.RINGING)
                .startedAt(Instant.now())
                .build());

        CallResponse response = CallResponse.from(session, iceServerProvider.iceServers());
        sendToUser(calleeId, Map.of("event", "INCOMING_CALL", "call", response));
        log.info("Call {} started by {} in conversation {}", session.getId(), callerId, conversationId);
        return response;
    }

    @Transactional
    public CallResponse accept(UUID userId, UUID callId) {
        CallSession session = requireParticipant(userId, callId);
        if (!session.getCalleeId().equals(userId)) {
            throw new ForbiddenException("Only the person being called can accept");
        }
        if (session.getStatus() != CallStatus.RINGING) {
            throw new BusinessException(ErrorCode.CONFLICT, "That call is no longer ringing");
        }
        session.setStatus(CallStatus.ACTIVE);
        session.setAnsweredAt(Instant.now());
        callRepository.save(session);

        CallResponse response = CallResponse.from(session, iceServerProvider.iceServers());
        sendToUser(session.getCallerId(), Map.of("event", "CALL_ACCEPTED", "call", response));
        return response;
    }

    @Transactional
    public CallResponse decline(UUID userId, UUID callId) {
        return finish(requireParticipant(userId, callId), userId, CallStatus.DECLINED, "DECLINED");
    }

    @Transactional
    public CallResponse hangUp(UUID userId, UUID callId) {
        CallSession session = requireParticipant(userId, callId);
        CallStatus finalStatus = session.getStatus() == CallStatus.RINGING
                ? CallStatus.MISSED : CallStatus.ENDED;
        return finish(session, userId, finalStatus, "HANGUP");
    }

    /** Relays one signalling frame to the other party. */
    @Transactional(readOnly = true)
    public void relay(UUID senderId, SignalMessage signal) {
        CallSession session = requireParticipant(senderId, signal.callId());
        if (!session.isLive()) {
            log.debug("Dropping {} for finished call {}", signal.type(), signal.callId());
            return;
        }
        sendToUser(session.otherParty(senderId), Map.of(
                "event", "SIGNAL",
                "callId", signal.callId(),
                "type", signal.type(),
                "from", senderId,
                "payload", signal.payload() == null ? "" : signal.payload()));
    }

    @Transactional(readOnly = true)
    public List<CallResponse> history(UUID userId, UUID conversationId, Pageable pageable) {
        chatService.requireParticipant(userId, conversationId);
        return callRepository.findAllByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(session -> CallResponse.from(session, List.of()))
                .getContent();
    }

    /** Sweeps calls nobody picked up, so a crashed client cannot leave a phone ringing forever. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleCalls() {
        List<CallSession> stale =
                callRepository.findStaleRinging(CallStatus.RINGING, Instant.now().minus(RING_TIMEOUT));
        for (CallSession session : stale) {
            finish(session, session.getCallerId(), CallStatus.MISSED, "TIMEOUT");
        }
        if (!stale.isEmpty()) {
            log.info("Expired {} unanswered calls", stale.size());
        }
    }

    // ---- internals -----------------------------------------------------

    private CallResponse finish(CallSession session, UUID actorId, CallStatus status, String reason) {
        if (!session.isLive()) {
            return CallResponse.from(session, List.of());
        }
        Instant now = Instant.now();
        session.setStatus(status);
        session.setEndedAt(now);
        session.setEndReason(reason);
        if (session.getAnsweredAt() != null) {
            session.setDurationSeconds((int) Duration.between(session.getAnsweredAt(), now).toSeconds());
        }
        callRepository.save(session);

        CallResponse response = CallResponse.from(session, List.of());
        sendToUser(session.otherParty(actorId), Map.of("event", "CALL_ENDED", "call", response));

        chatService.appendSystemMessage(session.getConversationId(), session.getCallerId(),
                Message.MessageType.CALL_SUMMARY, summaryOf(session));

        if (status == CallStatus.MISSED) {
            notificationService.notifyAsync(session.getCalleeId(), NotificationType.MISSED_CALL,
                    "Missed call", "You missed a call", session.getCallerId(),
                    "CALL", session.getId(), null);
        }
        return response;
    }

    private String summaryOf(CallSession session) {
        return switch (session.getStatus()) {
            case ENDED -> session.getDurationSeconds() == null
                    ? "Call ended" : "Call - " + formatDuration(session.getDurationSeconds());
            case DECLINED -> "Call declined";
            case MISSED -> "Missed call";
            default -> "Call";
        };
    }

    private String formatDuration(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private CallSession requireParticipant(UUID userId, UUID callId) {
        CallSession session = callRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Call", callId));
        if (!session.involves(userId)) {
            throw new ForbiddenException("You are not part of this call");
        }
        return session;
    }

    private void sendToUser(UUID userId, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), USER_CALL_QUEUE, payload);
        } catch (Exception e) {
            log.debug("Call event to {} could not be delivered: {}", userId, e.getMessage());
        }
    }
}
