package com.dating.platform.profile.repository;

import com.dating.platform.profile.entity.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @EntityGraph(attributePaths = {"interests", "qualities", "languages"})
    Optional<Profile> findByUserId(UUID userId);

    Optional<Profile> findFirstByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * Batch load for the matching engine - avoids N+1 across the candidate pool.
     *
     * <p>Only one collection is fetch-joined: joining interests and qualities together
     * returns profiles x interests x qualities rows. Qualities are batch-loaded instead
     * (hibernate.default_batch_fetch_size), so every caller must be inside a transaction.
     */
    @EntityGraph(attributePaths = {"interests"})
    @Query("select p from Profile p where p.user.id in :userIds")
    List<Profile> findAllByUserIdIn(@Param("userIds") List<UUID> userIds);
}
