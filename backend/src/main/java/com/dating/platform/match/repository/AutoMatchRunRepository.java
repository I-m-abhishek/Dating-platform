package com.dating.platform.match.repository;

import com.dating.platform.match.entity.AutoMatchRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutoMatchRunRepository extends JpaRepository<AutoMatchRun, UUID> {

    boolean existsByUserIdAndCadenceAndPeriodKey(UUID userId, AutoMatchRun.Cadence cadence, String periodKey);

    Optional<AutoMatchRun> findByUserIdAndCadenceAndPeriodKey(UUID userId, AutoMatchRun.Cadence cadence,
                                                              String periodKey);

    List<AutoMatchRun> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByPeriodKeyAndOutcome(String periodKey, AutoMatchRun.Outcome outcome);

    /**
     * Everyone whose slot for this period is already spent. Feeding this into the candidate
     * exclusion list is what stops one popular profile from absorbing every auto-match in a run.
     */
    @Query("""
            select r.userId from AutoMatchRun r
            where r.cadence = :cadence
              and r.periodKey = :periodKey
              and r.outcome = :outcome
            """)
    List<UUID> findUserIdsWithOutcome(@Param("cadence") AutoMatchRun.Cadence cadence,
                                      @Param("periodKey") String periodKey,
                                      @Param("outcome") AutoMatchRun.Outcome outcome);
}
