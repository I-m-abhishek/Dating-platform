package com.dating.platform.profile.repository;

import com.dating.platform.profile.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findAllByUserIdOrderByDisplayOrderAsc(UUID userId);

    Optional<Photo> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("select p from Photo p where p.user.id in :userIds order by p.displayOrder asc")
    List<Photo> findAllByUserIds(@Param("userIds") List<UUID> userIds);

    @Modifying
    @Query("update Photo p set p.primaryPhoto = false where p.user.id = :userId")
    void clearPrimary(@Param("userId") UUID userId);

    @Modifying
    @Query("update Photo p set p.commentCount = p.commentCount + :delta where p.id = :photoId")
    void adjustCommentCount(@Param("photoId") UUID photoId, @Param("delta") int delta);
}
