package com.dating.platform.comment.repository;

import com.dating.platform.comment.entity.PhotoComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PhotoCommentRepository extends JpaRepository<PhotoComment, UUID> {

    @Query("""
            select c from PhotoComment c
            where c.photoId = :photoId
              and c.hidden = false
              and c.parentCommentId is null
              and c.authorId not in :hiddenAuthors
            order by c.createdAt desc
            """)
    Page<PhotoComment> findTopLevel(@Param("photoId") UUID photoId,
                                    @Param("hiddenAuthors") Collection<UUID> hiddenAuthors,
                                    Pageable pageable);

    /** Replies for a whole page of top-level comments in one query. */
    List<PhotoComment> findAllByParentCommentIdInAndHiddenFalseOrderByCreatedAtAsc(
            Collection<UUID> parentCommentIds);

    Page<PhotoComment> findAllByPhotoOwnerIdAndHiddenFalseOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    @Modifying
    @Query("update PhotoComment c set c.replyCount = c.replyCount + 1 where c.id = :id")
    void incrementReplyCount(@Param("id") UUID id);
}
