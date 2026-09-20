package com.dating.platform.user.repository;

import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.UserStatus;
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
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u where u.id = :id and u.status = :status")
    Optional<User> findByIdAndStatus(@Param("id") UUID id, @Param("status") UserStatus status);

    List<User> findAllByStatus(UserStatus status);

    /** Everyone eligible for the auto-match run: active, located, onboarded. */
    @Query("""
            select u from User u
            where u.status = com.dating.platform.user.entity.enums.UserStatus.ACTIVE
              and u.latitude is not null
              and u.longitude is not null
              and u.onboardingCompletedAt is not null
            """)
    List<User> findAutoMatchEligible();

    @Modifying
    @Query("update User u set u.lastActiveAt = :now where u.id = :id")
    void touchLastActive(@Param("id") UUID id, @Param("now") Instant now);
}
