package com.dating.platform.chat.repository;

import com.dating.platform.chat.entity.Message;
import org.springframework.data.domain.Pageable;
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
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Keyset pagination: newest first, strictly older than the cursor. Offset pagination
     * would skip or repeat messages as new ones arrive mid-scroll.
     *
     * <p>The caller always supplies a cursor - for the first page it passes a timestamp in
     * the near future. That keeps the parameter non-null, which avoids the
     * "could not determine type" trap of a nullable comparison parameter in JPQL.
     */
    @EntityGraph(attributePaths = "attachments")
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and m.createdAt < :before
            order by m.createdAt desc
            """)
    List<Message> findPage(@Param("conversationId") UUID conversationId,
                           @Param("before") Instant before,
                           Pageable pageable);

    @EntityGraph(attributePaths = "attachments")
    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    long countByConversationIdAndSenderId(UUID conversationId, UUID senderId);

    Optional<Message> findFirstByClientMessageIdAndSenderId(String clientMessageId, UUID senderId);

    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.conversationId = :conversationId
              and m.senderId <> :readerId
              and m.readAt is null
            """)
    int markRead(@Param("conversationId") UUID conversationId,
                 @Param("readerId") UUID readerId,
                 @Param("now") Instant now);
}
