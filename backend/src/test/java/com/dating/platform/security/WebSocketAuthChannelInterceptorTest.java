package com.dating.platform.security;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketAuthChannelInterceptorTest {

    /**
     * Every server-side sender addresses users as {@code convertAndSendToUser(userId.toString())},
     * and Spring routes {@code /user/**} by the session principal's name. If the name were the
     * email, notifications and incoming calls would silently go nowhere.
     */
    @Test
    void stompSessionIsNamedByUserIdSoUserQueuesRoute() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.of(userId, "someone@example.com", null, true, Set.of("USER"));

        var authentication = new WebSocketAuthChannelInterceptor.StompAuthentication(principal);

        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.isAuthenticated()).isTrue();
    }
}
