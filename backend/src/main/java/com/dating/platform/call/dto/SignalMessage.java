package com.dating.platform.call.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One WebRTC signalling frame, relayed verbatim between the two peers.
 *
 * <p>The server does not parse {@code payload} - it is SDP or an ICE candidate and belongs
 * to the peers. The server's only job is to check that the sender is really in this call
 * and forward it to the other party.
 */
@Schema(name = "SignalMessage")
public record SignalMessage(

        @NotNull UUID callId,

        @NotNull Type type,

        /** Raw SDP or ICE candidate JSON, opaque to the server. */
        String payload
) {

    public enum Type {
        OFFER,
        ANSWER,
        ICE_CANDIDATE,
        HANGUP,
        /**
         * {@code {"audio":bool,"video":bool}} - whether the sender's mic and camera are on.
         * Browsers do not reliably tell the other side that a camera was switched off (the
         * last frame just freezes on screen), so each peer announces it explicitly.
         */
        MEDIA_STATE
    }
}
