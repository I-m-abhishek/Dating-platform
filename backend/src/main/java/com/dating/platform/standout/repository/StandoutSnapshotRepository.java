package com.dating.platform.standout.repository;

import com.dating.platform.standout.entity.StandoutSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dating.platform.user.entity.enums.Gender;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StandoutSnapshotRepository extends JpaRepository<StandoutSnapshot, UUID> {

    @Query("select max(s.cycleKey) from StandoutSnapshot s")
    Optional<String> findLatestCycleKey();

    /**
     * The top of the shelf minus everyone the viewer must not see: themselves, blocks in
     * either direction, existing matches, and people already liked or recently passed.
     * Correlated {@code NOT EXISTS} checks hit the pair indexes instead of shipping an
     * ever-growing id list from Java.
     *
     * <p>Gender is filtered here too, both ways: the candidate must be a gender the viewer
     * wants, and must want (or not have restricted) the viewer's gender. Filtering in SQL
     * rather than after the fetch keeps the shelf full for everyone, not just the majority.
     */
    @Query("""
            select s from StandoutSnapshot s
            where s.cycleKey = :cycleKey
              and s.userId <> :viewerId
              and exists (select 1 from User u
                          where u.id = s.userId
                            and u.gender in :wantedGenders
                            and (:viewerGender member of u.interestedIn or u.interestedIn is empty))
              and not exists (select 1 from Like l
                              where l.senderId = :viewerId and l.receiverId = s.userId)
              and not exists (select 1 from Pass p
                              where p.senderId = :viewerId and p.receiverId = s.userId
                                and p.expiresAt > :now)
              and not exists (select 1 from Match m
                              where (m.userAId = :viewerId and m.userBId = s.userId)
                                 or (m.userBId = :viewerId and m.userAId = s.userId))
              and not exists (select 1 from Block b
                              where (b.blockerId = :viewerId and b.blockedId = s.userId)
                                 or (b.blockerId = s.userId and b.blockedId = :viewerId))
            order by s.rankPosition asc
            """)
    List<StandoutSnapshot> findTopFor(@Param("cycleKey") String cycleKey,
                                      @Param("viewerId") UUID viewerId,
                                      @Param("viewerGender") Gender viewerGender,
                                      @Param("wantedGenders") Collection<Gender> wantedGenders,
                                      @Param("now") Instant now,
                                      org.springframework.data.domain.Pageable pageable);

    void deleteAllByCycleKeyNot(String cycleKey);
}
