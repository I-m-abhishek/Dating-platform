package com.dating.platform.standout.repository;

import com.dating.platform.standout.entity.StandoutSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StandoutSnapshotRepository extends JpaRepository<StandoutSnapshot, UUID> {

    @Query("select max(s.cycleKey) from StandoutSnapshot s")
    Optional<String> findLatestCycleKey();

    @Query("""
            select s from StandoutSnapshot s
            where s.cycleKey = :cycleKey
              and s.userId not in :excludedIds
            order by s.rankPosition asc
            """)
    List<StandoutSnapshot> findTop(@Param("cycleKey") String cycleKey,
                                   @Param("excludedIds") Collection<UUID> excludedIds,
                                   org.springframework.data.domain.Pageable pageable);

    void deleteAllByCycleKeyNot(String cycleKey);
}
