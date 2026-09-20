package com.dating.platform.call.controller;

import com.dating.platform.call.dto.SignalMessage;
import com.dating.platform.call.service.CallService;
import com.dating.platform.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * WebRTC signalling relay.
 *
 * <p>Clients publish to {@code /app/calls/signal} and receive frames on their private
 * {@code /user/queue/calls}. The server verifies call membership and forwards; it never
 * parses the SDP or candidates.
 */
@Controller
@RequiredArgsConstructor
public class CallSignalingController {

    private final CallService callService;

    @MessageMapping("/calls/signal")
    public void signal(@Valid @Payload SignalMessage message, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Unauthenticated signalling frame");
        }
        callService.relay(principal.getId(), message);
    }
}
