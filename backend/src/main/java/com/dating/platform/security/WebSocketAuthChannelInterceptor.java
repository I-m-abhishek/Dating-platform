package com.dating.platform.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticates the STOMP CONNECT frame from the {@code Authorization} header.
 *
 * <p>The principal is attached to the session, so {@code @MessageMapping} handlers
 * receive a real {@link java.security.Principal} and {@code /user/**} destinations
 * route to the right socket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = firstHeader(accessor, "Authorization");
        if (token != null && token.startsWith(PREFIX)) {
            token = token.substring(PREFIX.length()).trim();
        }
        if (!StringUtils.hasText(token)) {
            log.debug("STOMP CONNECT without credentials - rejecting");
            return null;
        }

        Claims claims = tokenProvider.parse(token);
        if (claims == null || !tokenProvider.isAccessToken(claims)) {
            log.debug("STOMP CONNECT with invalid token - rejecting");
            return null;
        }

        UUID userId = tokenProvider.userIdFrom(claims);
        Set<String> roles = tokenProvider.rolesFrom(claims);
        UserPrincipal principal = UserPrincipal.of(userId, tokenProvider.emailFrom(claims), null, true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        accessor.setUser(auth);
        return message;
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
