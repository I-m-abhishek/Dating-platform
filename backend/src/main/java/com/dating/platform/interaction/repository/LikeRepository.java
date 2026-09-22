package com.dating.platform.interaction.repository;

import com.dating.platform.interaction.entity.Like;
import com.dating.platform.interaction.entity.LikeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LikeRepository extends JpaRepository<Like, UUID> {

    Optional<Like> findBySenderIdAndReceiverId(UUID senderId, UUID receiverId);

    /** Idempotency lookup: has this exact client request already been applied? */
    Optional<Like> findBySenderIdAndClientLikeId(UUID senderId, String clientLikeId);

    boolean existsBySenderIdAndReceiverId(UUID senderId, UUID receiverId);

    /** The Likes You tab: pending inbound likes, newest and SUPER first. */
    @Query("""
            select l from Like l
            where l.receiverId = :userId
              and l.status = :status
              and l.senderId not in :hiddenIds
            order by l.type desc, l.createdAt desc
            """)
    Page<Like> findInbound(@Param("userId") UUID userId,
                           @Param("status") LikeStatus status,
                           @Param("hiddenIds") Collection<UUID> hiddenIds,
                           Pageable pageable);

    @Query("""
            select count(l) from Like l
            where l.receiverId = :userId and l.status = :status
            """)
    long countInbound(@Param("userId") UUID userId, @Param("status") LikeStatus status);

    @Query("""
            select count(l) from Like l
            where l.receiverId = :userId and l.status = :status and l.seen = false
            """)
    long countUnseenInbound(@Param("userId") UUID userId, @Param("status") LikeStatus status);

    /** Everyone this user has already acted on - excluded from discovery. */
    @Query("select l.receiverId from Like l where l.senderId = :userId")
    List<UUID> findReceiverIdsBySender(@Param("userId") UUID userId);

    @Modifying
    @Query("""
            update Like l set l.seen = true
            where l.receiverId = :userId and l.seen = false
            """)
    int markInboundSeen(@Param("userId") UUID userId);

    @Modifying
    @Query("update Like l set l.status = :status where l.id in :ids")
    int updateStatus(@Param("ids") Collection<UUID> ids, @Param("status") LikeStatus status);

    long countBySenderIdAndCreatedAtAfter(UUID senderId, Instant after);
}
