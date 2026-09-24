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

    /** The Likes You tab: pending inbound likes, SUPER first (they are the priority lane), then newest. */
    @Query("""
            select l from Like l
            where l.receiverId = :userId
              and l.status = :status
              and l.senderId not in :hiddenIds
            order by case when l.type = com.dating.platform.interaction.entity.LikeType.SUPER then 0 else 1 end,
                     l.createdAt desc
            """)
    Page<Like> findInbound(@Param("userId") UUID userId,
                           @Param("status") LikeStatus status,
                           @Param("hiddenIds") Collection<UUID> hiddenIds,
                           Pageable pageable);

    /** Both badge numbers for the Likes You tab in a single pass over the receiver index. */
    @Query("""
            select count(l) as total,
                   coalesce(sum(case when l.seen = false then 1L else 0L end), 0L) as unseen
            from Like l
            where l.receiverId = :userId and l.status = :status
            """)
    InboundCounts countInboundAndUnseen(@Param("userId") UUID userId, @Param("status") LikeStatus status);

    interface InboundCounts {
        long getTotal();

        long getUnseen();
    }

    @Query("""
            select count(l) from Like l
            where l.receiverId = :userId and l.status = :status and l.seen = false
            """)
    long countUnseenInbound(@Param("userId") UUID userId, @Param("status") LikeStatus status);


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

    /** Of these people, who has a pending super like waiting on {@code receiverId} - for the feed's priority lane. */
    @Query("""
            select l.senderId from Like l
            where l.receiverId = :receiverId
              and l.senderId in :senderIds
              and l.status = com.dating.platform.interaction.entity.LikeStatus.PENDING
              and l.type = com.dating.platform.interaction.entity.LikeType.SUPER
            """)
    List<UUID> findPendingSuperLikers(@Param("receiverId") UUID receiverId,
                                      @Param("senderIds") Collection<UUID> senderIds);
}
