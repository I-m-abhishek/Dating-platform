package com.dating.platform.quota.service;

import com.dating.platform.common.exception.QuotaExceededException;
import com.dating.platform.quota.entity.QuotaFeature;
import com.dating.platform.quota.entity.UsageCounter;
import com.dating.platform.quota.repository.UsageCounterRepository;
import com.dating.platform.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Enforces metered allowances - "5 photo comments a day", "one auto-match a week".
 *
 * <p>Concurrency: consumption is a single conditional UPDATE, so two simultaneous
 * requests can never both spend the last token. The row is created lazily on first use.
 *
 * <p>This is product metering, not abuse protection. Abuse protection is
 * {@code com.dating.platform.ratelimit}, which is per-IP and in memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UsageCounterRepository usageCounterRepository;
    private final UsageCounterInitializer usageCounterInitializer;

    /**
     * Spends one unit of the allowance.
     *
     * @param limit maximum for the period; a negative value means unlimited
     * @throws QuotaExceededException when the allowance is exhausted
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void consume(UUID userId, QuotaFeature feature, int limit, String upgradeHint) {
        String periodKey = periodKeyFor(feature);

        if (limit < 0) {
            usageCounterInitializer.ensureRow(userId, feature, periodKey);
            usageCounterRepository.consumeUnlimited(userId, feature, periodKey);
            return;
        }
        if (limit == 0) {
            throw new QuotaExceededException(feature.getLabel(), 0, resetAt(feature), upgradeHint);
        }

        usageCounterInitializer.ensureRow(userId, feature, periodKey);
        int updated = usageCounterRepository.consumeIfUnderLimit(userId, feature, periodKey, limit);
        if (updated == 0) {
            log.debug("Quota exhausted: user={} feature={} limit={}", userId, feature, limit);
            throw new QuotaExceededException(feature.getLabel(), limit, resetAt(feature), upgradeHint);
        }
    }

    /** Gives a unit back - used when the action that consumed it is rolled back by the user. */
    @Transactional
    public void refund(UUID userId, QuotaFeature feature) {
        usageCounterRepository.refund(userId, feature, periodKeyFor(feature));
    }

    @Transactional(readOnly = true)
    public int used(UUID userId, QuotaFeature feature) {
        return usageCounterRepository
                .findByUserIdAndFeatureAndPeriodKey(userId, feature, periodKeyFor(feature))
                .map(UsageCounter::getUsed)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public QuotaStatus status(UUID userId, QuotaFeature feature, int limit) {
        int used = used(userId, feature);
        int remaining = limit < 0 ? -1 : Math.max(0, limit - used);
        return new QuotaStatus(feature, limit, used, remaining, resetAt(feature));
    }

    private String periodKeyFor(QuotaFeature feature) {
        return feature.getPeriod() == QuotaFeature.Period.WEEKLY
                ? DateUtils.isoWeekKey(DateUtils.today())
                : DateUtils.today().toString();
    }

    private Instant resetAt(QuotaFeature feature) {
        return feature.getPeriod() == QuotaFeature.Period.WEEKLY
                ? DateUtils.nextWeeklyReset()
                : DateUtils.nextDailyReset();
    }

    /** Snapshot for the client, so the UI can show "3 of 5 comments left today". */
    public record QuotaStatus(QuotaFeature feature, int limit, int used, int remaining, Instant resetsAt) {
    }
}
