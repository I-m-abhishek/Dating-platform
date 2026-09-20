package com.dating.platform.interaction.repository;

import com.dating.platform.interaction.entity.Pass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PassRepository extends JpaRepository<Pass, UUID> {

    Optional<Pass> findBySenderIdAndReceiverId(UUID senderId, UUID receiverId);

    /** Only passes that have not cooled down still hide a profile. */
    @Query("select p.receiverId from Pass p where p.senderId = :userId and p.expiresAt > :now")
    List<UUID> findActiveReceiverIds(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<Pass> findFirstBySenderIdOrderByCreatedAtDesc(UUID senderId);

    void deleteBySenderIdAndReceiverId(UUID senderId, UUID receiverId);
}
