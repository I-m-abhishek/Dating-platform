package com.dating.platform.media.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An uploaded blob that has not been attached to anything yet.
 *
 * <p>Uploads are two-step: the client POSTs the file and gets back an asset id, then
 * references that id when sending the message. The alternative - letting the client pass a
 * storage key straight into the send call - would let anyone attach any object in the
 * bucket to their own message. The asset row records the owner, so the send path can verify
 * it.
 *
 * <p>{@code consumed} makes the id single use; unconsumed assets are swept periodically.
 */
@Entity
@Table(name = "media_assets", indexes = {
        @Index(name = "idx_media_assets_owner", columnList = "owner_id"),
        @Index(name = "idx_media_assets_consumed", columnList = "consumed,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset extends BaseUuidEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "url", nullable = false, length = 600)
    private String url;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "waveform", length = 1000)
    private String waveform;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private Purpose purpose;

    @Column(name = "consumed", nullable = false)
    @Builder.Default
    private boolean consumed = false;

    public enum Purpose {
        CHAT_ATTACHMENT,
        PROFILE_PHOTO,
        VERIFICATION
    }
}
