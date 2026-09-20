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

    /** Batch load for the matching engine - avoids N+1 across the candidate pool. */
    @EntityGraph(attributePaths = {"interests", "qualities"})
    @Query("select p from Profile p where p.user.id in :userIds")
    List<Profile> findAllByUserIdIn(@Param("userIds") List<UUID> userIds);
}
