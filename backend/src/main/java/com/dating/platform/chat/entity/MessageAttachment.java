package com.dating.platform.chat.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A file sent inside a conversation.
 *
 * <p>The blob itself lives in object storage; this row holds the key plus the metadata the
 * client needs to render a placeholder before the download finishes.
 */
@Entity
@Table(name = "message_attachments",
        indexes = @Index(name = "idx_message_attachments_message", columnList = "message_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_message_attachments_message"))
    private Message message;

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

    /** Voice notes and video: length in seconds. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Voice notes: normalised amplitude samples for the waveform, comma separated. */
    @Column(name = "waveform", length = 1000)
    private String waveform;

    @Column(name = "thumbnail_url", length = 600)
    private String thumbnailUrl;
}
