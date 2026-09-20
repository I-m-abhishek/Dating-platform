package com.dating.platform.auth.service;

import com.dating.platform.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Keeps the refresh token table from growing without bound. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    /** Expired tokens are kept briefly so replay detection still has something to match. */
    private static final Duration GRACE = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteExpired(Instant.now().minus(GRACE));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
