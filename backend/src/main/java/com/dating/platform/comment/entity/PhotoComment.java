package com.dating.platform.comment.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A comment left on someone's profile photo.
 *
 * <p>{@code photoOwnerId} is denormalised from the photo so the owner's inbox query and the
 * permission check never need to join {@code photos}.
 *
 * <p>Deletion is soft: the author or the photo owner can hide a comment, and moderation
 * needs the original text to remain readable.
 */
@Entity
@Table(name = "photo_comments", indexes = {
        @Index(name = "idx_photo_comments_photo", columnList = "photo_id,created_at"),
        @Index(name = "idx_photo_comments_owner", columnList = "photo_owner_id,created_at"),
        @Index(name = "idx_photo_comments_author", columnList = "author_id,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoComment extends BaseUuidEntity {

    @Column(name = "photo_id", nullable = false)
    private UUID photoId;

    @Column(name = "photo_owner_id", nullable = false)
    private UUID photoOwnerId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, length = 300)
    private String body;

    /** Set when this comment replies to another one. */
    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "hidden", nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(name = "hidden_by")
    private UUID hiddenBy;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(name = "reply_count", nullable = false)
    @Builder.Default
    private int replyCount = 0;
}
