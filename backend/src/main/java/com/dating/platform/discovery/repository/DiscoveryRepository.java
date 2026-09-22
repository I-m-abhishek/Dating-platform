package com.dating.platform.discovery.repository;

import com.dating.platform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The candidate query for discovery and auto-match.
 *
 * <p>Native SQL on purpose. Three things make it worth stepping outside JPQL:
 * the bounding-box pre-filter, the {@code EXISTS} checks against the preference and block
 * tables, and index-friendly ordering. The query returns ids only - hydration and exact
 * haversine distance happen in the service, where the candidate set is already small.
 *
 * <p>Bounding box, not a radius: it is a cheap index range scan. The exact circular
 * distance test runs afterwards in Java and discards the corners of the box.
 */
@Repository
public interface DiscoveryRepository extends JpaRepository<User, UUID> {

    @Query(value = """
            SELECT u.id
            FROM users u
            WHERE u.status = 'ACTIVE'
              AND u.id <> :viewerId
              AND u.onboarding_completed_at IS NOT NULL
              AND u.incognito = FALSE
              AND u.latitude IS NOT NULL
              AND u.longitude IS NOT NULL
              AND u.date_of_birth BETWEEN :oldestDob AND :youngestDob
              AND u.gender IN (:wantedGenders)
              AND u.preferred_min_age <= :viewerAge
              AND u.preferred_max_age >= :viewerAge
              AND (
                    :ignoreDistance = TRUE
                    OR (u.latitude BETWEEN :minLat AND :maxLat
                        AND u.longitude BETWEEN :minLon AND :maxLon)
                  )
              AND EXISTS (
                    SELECT 1 FROM user_interested_in ui
                    WHERE ui.user_id = u.id AND ui.gender = :viewerGender
                  )
              AND NOT EXISTS (
                    SELECT 1 FROM blocks b
                    WHERE (b.blocker_id = :viewerId AND b.blocked_id = u.id)
                       OR (b.blocker_id = u.id AND b.blocked_id = :viewerId)
                  )
              AND u.id NOT IN (:excludedIds)
            ORDER BY u.last_active_at DESC NULLS LAST, u.created_at DESC
            LIMIT :poolSize
            """, nativeQuery = true)
    List<UUID> findCandidateIds(@Param("viewerId") UUID viewerId,
                                @Param("viewerGender") String viewerGender,
                                @Param("viewerAge") int viewerAge,
                                @Param("wantedGenders") Collection<String> wantedGenders,
                                @Param("oldestDob") LocalDate oldestDob,
                                @Param("youngestDob") LocalDate youngestDob,
                                @Param("ignoreDistance") boolean ignoreDistance,
                                @Param("minLat") double minLat,
                                @Param("maxLat") double maxLat,
                                @Param("minLon") double minLon,
                                @Param("maxLon") double maxLon,
                                @Param("excludedIds") Collection<UUID> excludedIds,
                                @Param("poolSize") int poolSize);

    /**
     * Popularity signal for the Standouts shelf: likes received in the window, weighted by
     * how many of them were super likes, normalised by how long the account has existed so a
     * brand new profile is not permanently out-ranked by an old one.
     */
    @Query(value = """
            SELECT u.id AS user_id,
                   COUNT(l.id) AS like_count,
                   COALESCE(SUM(CASE WHEN l.type = 'SUPER' THEN 1 ELSE 0 END), 0) AS super_count
            FROM users u
            LEFT JOIN likes l
                   ON l.receiver_id = u.id
                  AND l.created_at >= :since
            WHERE u.status = 'ACTIVE'
              AND u.onboarding_completed_at IS NOT NULL
              AND u.incognito = FALSE
            GROUP BY u.id
            """, nativeQuery = true)
    List<Object[]> aggregateLikeCountsSince(@Param("since") java.time.Instant since);
}
