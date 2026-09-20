package com.dating.platform.match.repository;

import com.dating.platform.match.entity.Match;
import com.dating.platform.match.entity.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Query("""
            select m from Match m
            where (m.userAId = :a and m.userBId = :b) or (m.userAId = :b and m.userBId = :a)
            """)
    Optional<Match> findByPair(@Param("a") UUID a, @Param("b") UUID b);

    @Query("""
            select m from Match m
            where (m.userAId = :userId or m.userBId = :userId)
              and m.status = :status
            order by coalesce(m.lastInteractionAt, m.matchedAt) desc
            """)
    Page<Match> findAllForUser(@Param("userId") UUID userId,
                               @Param("status") MatchStatus status,
                               Pageable pageable);

    @Query("""
            select count(m) from Match m
            where (m.userAId = :userId or m.userBId = :userId) and m.status = :status
            """)
    long countForUser(@Param("userId") UUID userId, @Param("status") MatchStatus status);

    /** Every counterpart id, whatever the status - used to exclude them from discovery. */
    @Query("""
            select case when m.userAId = :userId then m.userBId else m.userAId end
            from Match m
            where m.userAId = :userId or m.userBId = :userId
            """)
    List<UUID> findAllCounterpartIds(@Param("userId") UUID userId);

    Optional<Match> findByConversationId(UUID conversationId);

    @Query("""
            select m from Match m
            where (m.userAId = :userId or m.userBId = :userId)
              and m.status = :status
              and m.source in :sources
            order by m.matchedAt desc
            """)
    List<Match> findBySource(@Param("userId") UUID userId,
                             @Param("status") MatchStatus status,
                             @Param("sources") List<com.dating.platform.match.entity.MatchSource> sources);
}
