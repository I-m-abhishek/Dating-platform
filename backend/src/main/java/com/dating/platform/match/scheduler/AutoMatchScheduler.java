package com.dating.platform.match.scheduler;

import com.dating.platform.match.entity.AutoMatchRun.Cadence;
import com.dating.platform.match.service.AutoMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives the scheduled auto-match runs.
 *
 * <p>Deliberately thin: it decides <em>when</em> and <em>for whom</em>, and delegates the
 * actual matching. Each user is processed in its own transaction inside
 * {@link AutoMatchService#runFor}, so one failure does not roll back the batch - and because
 * the call crosses a bean boundary, the {@code REQUIRES_NEW} on that method really applies.
 *
 * <p><b>Multi-instance note.</b> On more than one node these crons would fire in parallel.
 * The unique constraint on {@code auto_match_runs} makes duplicate work harmless, but for a
 * real deployment put a lock in front of this - ShedLock is the usual answer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoMatchScheduler {

    private final AutoMatchService autoMatchService;

    /** Everyone gets one free match a week. */
    @Scheduled(cron = "${app.matching.weekly-auto-match-cron}", zone = "UTC")
    public void runWeekly() {
        run(Cadence.WEEKLY);
    }

    /** Premium members also get one a day. */
    @Scheduled(cron = "${app.matching.daily-auto-match-cron}", zone = "UTC")
    public void runDaily() {
        run(Cadence.DAILY);
    }

    private void run(Cadence cadence) {
        List<UUID> userIds = autoMatchService.eligibleUserIds(cadence);
        log.info("Auto-match {} run starting for {} users", cadence, userIds.size());

        AtomicInteger matched = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (UUID userId : userIds) {
            try {
                var response = autoMatchService.runFor(userId, cadence);
                if (response.match() != null) {
                    matched.incrementAndGet();
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Auto-match {} failed for user {}", cadence, userId, e);
            }
        }

        log.info("Auto-match {} run finished: {} matched, {} failed, {} considered",
                cadence, matched.get(), failed.get(), userIds.size());
    }
}
