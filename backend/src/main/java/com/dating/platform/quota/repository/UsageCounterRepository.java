package com.dating.platform.quota.repository;

import com.dating.platform.quota.entity.QuotaFeature;
import com.dating.platform.quota.entity.UsageCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageCounterRepository extends JpaRepository<UsageCounter, UUID> {

    Optional<UsageCounter> findByUserIdAndFeatureAndPeriodKey(UUID userId, QuotaFeature feature, String periodKey);

    /**
     * Atomic consume. The {@code used < :limit} predicate is what makes the check and the
     * increment a single statement - two concurrent requests cannot both pass the check.
     *
     * @return 1 when a token was consumed, 0 when the row is missing or the limit is reached
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UsageCounter c
            set c.used = c.used + 1
            where c.userId = :userId
              and c.feature = :feature
              and c.periodKey = :periodKey
              and c.used < :limit
            """)
    int consumeIfUnderLimit(@Param("userId") UUID userId,
                            @Param("feature") QuotaFeature feature,
                            @Param("periodKey") String periodKey,
                            @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UsageCounter c
            set c.used = c.used + 1
            where c.userId = :userId
              and c.feature = :feature
              and c.periodKey = :periodKey
            """)
    int consumeUnlimited(@Param("userId") UUID userId,
                         @Param("feature") QuotaFeature feature,
                         @Param("periodKey") String periodKey);

    @Modifying
    @Query("""
            update UsageCounter c
            set c.used = case when c.used > 0 then c.used - 1 else 0 end
            where c.userId = :userId
              and c.feature = :feature
              and c.periodKey = :periodKey
            """)
    int refund(@Param("userId") UUID userId,
               @Param("feature") QuotaFeature feature,
               @Param("periodKey") String periodKey);
}
