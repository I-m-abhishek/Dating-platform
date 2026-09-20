package com.dating.platform.call.repository;

import com.dating.platform.call.entity.CallSession;
import com.dating.platform.call.entity.CallSession.CallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {

    @Query("""
            select c from CallSession c
            where c.conversationId = :conversationId
              and c.status in :liveStatuses
            order by c.createdAt desc
            """)
    List<CallSession> findLive(@Param("conversationId") UUID conversationId,
                               @Param("liveStatuses") Collection<CallStatus> liveStatuses);

    @Query("""
            select c from CallSession c
            where (c.callerId = :userId or c.calleeId = :userId)
              and c.status in :liveStatuses
            """)
    List<CallSession> findLiveForUser(@Param("userId") UUID userId,
                                      @Param("liveStatuses") Collection<CallStatus> liveStatuses);

    Page<CallSession> findAllByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Optional<CallSession> findByIdAndConversationId(UUID id, UUID conversationId);

    /** Calls nobody answered - swept so a crashed client does not leave a phantom ringing. */
    @Query("""
            select c from CallSession c
            where c.status = :ringing and c.startedAt < :before
            """)
    List<CallSession> findStaleRinging(@Param("ringing") CallStatus ringing, @Param("before") Instant before);
}
