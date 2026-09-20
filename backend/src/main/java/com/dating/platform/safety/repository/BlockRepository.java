package com.dating.platform.safety.repository;

import com.dating.platform.safety.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlockRepository extends JpaRepository<Block, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("""
            select count(b) > 0 from Block b
            where (b.blockerId = :a and b.blockedId = :b)
               or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    /** Every user id that must be hidden from this user, in both directions. */
    @Query("""
            select case when b.blockerId = :userId then b.blockedId else b.blockerId end
            from Block b
            where b.blockerId = :userId or b.blockedId = :userId
            """)
    List<UUID> findAllCounterpartIds(@Param("userId") UUID userId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
