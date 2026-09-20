package com.dating.platform.call.dto;

import com.dating.platform.call.entity.CallSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "CallResponse")
public record CallResponse(
        UUID id,
        UUID conversationId,
        UUID callerId,
        UUID calleeId,
        CallSession.CallType type,
        CallSession.CallStatus status,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt,
        Integer durationSeconds,
        List<IceServer> iceServers
) {

    /** Passed straight to {@code RTCPeerConnection}. */
    @Schema(name = "IceServer")
    public record IceServer(List<String> urls, String username, String credential) {
    }

    public static CallResponse from(CallSession session, List<IceServer> iceServers) {
        return new CallResponse(session.getId(), session.getConversationId(), session.getCallerId(),
                session.getCalleeId(), session.getType(), session.getStatus(), session.getStartedAt(),
                session.getAnsweredAt(), session.getEndedAt(), session.getDurationSeconds(), iceServers);
    }
}
