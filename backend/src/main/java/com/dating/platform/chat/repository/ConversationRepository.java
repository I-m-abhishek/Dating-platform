package com.dating.platform.chat.repository;

import com.dating.platform.chat.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByMatchId(UUID matchId);

    @Query("""
            select c from Conversation c
            where (c.userAId = :userId or c.userBId = :userId)
              and c.status = :status
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    Page<Conversation> findAllForUser(@Param("userId") UUID userId,
                                      @Param("status") Conversation.ConversationStatus status,
                                      Pageable pageable);

    @Query("""
            select coalesce(sum(case when c.userAId = :userId then c.unreadForUserA else c.unreadForUserB end), 0)
            from Conversation c
            where c.userAId = :userId or c.userBId = :userId
            """)
    long totalUnreadFor(@Param("userId") UUID userId);
}
