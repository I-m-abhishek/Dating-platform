package com.dating.platform.quota.service;

import com.dating.platform.quota.entity.QuotaFeature;
import com.dating.platform.quota.entity.UsageCounter;
import com.dating.platform.quota.repository.UsageCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates the counter row for a (user, feature, period) the first time it is touched.
 *
 * <p>Separate bean on purpose: {@code REQUIRES_NEW} only takes effect through a proxy, so
 * calling this from {@link QuotaService} must be a real bean-to-bean call. The new
 * transaction means a lost insert race commits nothing and never marks the caller's
 * transaction rollback-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageCounterInitializer {

    private final UsageCounterRepository usageCounterRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureRow(UUID userId, QuotaFeature feature, String periodKey) {
        if (usageCounterRepository.findByUserIdAndFeatureAndPeriodKey(userId, feature, periodKey).isPresent()) {
            return;
        }
        try {
            usageCounterRepository.saveAndFlush(UsageCounter.builder()
                    .userId(userId)
                    .feature(feature)
                    .periodKey(periodKey)
                    .used(0)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.trace("Usage counter created concurrently for {}/{}", userId, feature);
        }
    }
}
