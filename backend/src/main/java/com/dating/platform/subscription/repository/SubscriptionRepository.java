package com.dating.platform.subscription.repository;

import com.dating.platform.subscription.entity.Subscription;
import com.dating.platform.subscription.entity.Subscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Active subscriptions, newest expiry first. A user could in theory hold more than one
     * (upgrade before the old period ends), so the entitlement service takes the highest tier.
     */
    @EntityGraph(attributePaths = "plan")
    @Query("""
            select s from Subscription s
            where s.userId = :userId
              and s.status = :status
              and s.currentPeriodEnd > :now
            order by s.currentPeriodEnd desc
            """)
    List<Subscription> findActiveForUser(@Param("userId") UUID userId,
                                         @Param("status") SubscriptionStatus status,
                                         @Param("now") Instant now);

    @EntityGraph(attributePaths = "plan")
    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Nightly sweep so lapsed subscriptions stop granting entitlements even if no webhook arrived. */
    @Modifying
    @Query("""
            update Subscription s
            set s.status = :expired
            where s.currentPeriodEnd <= :now
              and s.status = :active
            """)
    int expireLapsed(@Param("now") Instant now,
                     @Param("active") SubscriptionStatus active,
                     @Param("expired") SubscriptionStatus expired);
}
