package com.dating.platform.profile.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import com.dating.platform.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One profile photo. Photos are the target of the comment feature, so they carry
 * their own denormalised counters to keep the grid query cheap.
 */
@Entity
@Table(name = "photos", indexes = {
        @Index(name = "idx_photos_user", columnList = "user_id"),
        @Index(name = "idx_photos_user_order", columnList = "user_id,display_order")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Photo extends BaseUuidEntity {

    public static final int MAX_PHOTOS_PER_USER = 9;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_photos_user"))
    private User user;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "url", nullable = false, length = 600)
    private String url;

    @Column(name = "blurhash", length = 64)
    private String blurhash;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "caption", length = 140)
    private String caption;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primaryPhoto = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    public enum ModerationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
