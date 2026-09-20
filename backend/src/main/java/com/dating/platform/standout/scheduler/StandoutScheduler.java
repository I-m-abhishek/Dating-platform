package com.dating.platform.standout.scheduler;

import com.dating.platform.standout.service.StandoutRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StandoutScheduler {

    private final StandoutRefreshService standoutRefreshService;

    @Scheduled(cron = "${app.matching.standout-refresh-cron}", zone = "UTC")
    public void refresh() {
        try {
            standoutRefreshService.refresh();
        } catch (Exception e) {
            log.error("Standout refresh failed - the previous cycle stays live", e);
        }
    }

    /** Warm the shelf on boot so a fresh environment is not empty until the first cron fires. */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refresh();
    }
}
