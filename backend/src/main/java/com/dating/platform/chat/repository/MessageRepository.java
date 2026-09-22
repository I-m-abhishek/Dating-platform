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
    /**
     * One page of history, newest first.
     *
     * <p>The cursor is the pair {@code (createdAt, id)}, not the timestamp alone. Two
     * messages can share a millisecond - two people answering at once, or a burst from one
     * of them - and with only {@code createdAt < :before} that tie makes the page boundary
     * ambiguous: whichever of the two the database happens to order second is either
     * returned twice or skipped entirely. Comparing the id as a tiebreaker makes the order
     * total, so every message appears exactly once across the pages.
     */
    @EntityGraph(attributePaths = "attachments")
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and (m.createdAt < :before
                   or (m.createdAt = :before and m.id < :beforeId))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findPage(@Param("conversationId") UUID conversationId,
                           @Param("before") Instant before,
                           @Param("beforeId") UUID beforeId,
                           Pageable pageable);

    @EntityGraph(attributePaths = "attachments")
    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    long countByConversationIdAndSenderId(UUID conversationId, UUID senderId);

    Optional<Message> findFirstByClientMessageIdAndSenderId(String clientMessageId, UUID senderId);

    /**
     * Newest message in the conversation not sent by this user.
     *
     * <p>Used to set the reader's watermark. Replaces an UPDATE across every unread row
     * with one read and one column write on the conversation.
     */
    Optional<Message> findFirstByConversationIdAndSenderIdNotOrderByCreatedAtDesc(
            UUID conversationId, UUID senderId);
}
