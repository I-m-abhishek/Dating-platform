package com.dating.platform.call.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one call between matched users.
 *
 * <p>Only signalling and bookkeeping live on the server: the offer/answer/ICE exchange is
 * relayed over STOMP and the audio itself flows peer to peer over WebRTC. No media ever
 * touches the API, which is both a privacy property and the reason a single node can carry
 * a lot of concurrent calls.
 */
@Entity
@Table(name = "call_sessions", indexes = {
        @Index(name = "idx_call_sessions_conversation", columnList = "conversation_id,created_at"),
        @Index(name = "idx_call_sessions_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession extends BaseUuidEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "caller_id", nullable = false)
    private UUID callerId;

    @Column(name = "callee_id", nullable = false)
    private UUID calleeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    @Builder.Default
    private CallType type = CallType.VOICE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CallStatus status = CallStatus.RINGING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "end_reason", length = 40)
    private String endReason;

    public boolean isLive() {
        return status == CallStatus.RINGING || status == CallStatus.ACTIVE;
    }

    public boolean involves(UUID userId) {
        return callerId.equals(userId) || calleeId.equals(userId);
    }

    public UUID otherParty(UUID userId) {
        return callerId.equals(userId) ? calleeId : callerId;
    }

    public enum CallType {
        VOICE,
        VIDEO
    }

    public enum CallStatus {
        RINGING,
        ACTIVE,
        ENDED,
        DECLINED,
        MISSED,
        FAILED
    }
}
