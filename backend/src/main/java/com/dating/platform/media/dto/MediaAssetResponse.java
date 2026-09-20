package com.dating.platform.media.dto;

import com.dating.platform.media.entity.MediaAsset;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Handed back after an upload. {@code id} is what the client passes to
 * {@code POST /conversations/{id}/messages} - never the storage key.
 */
@Schema(name = "MediaAssetResponse")
public record MediaAssetResponse(
        UUID id,
        String url,
        String contentType,
        String fileName,
        long sizeBytes,
        Integer width,
        Integer height,
        Integer durationSeconds
) {

    public static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(asset.getId(), asset.getUrl(), asset.getContentType(),
                asset.getFileName(), asset.getSizeBytes(), asset.getWidth(), asset.getHeight(),
                asset.getDurationSeconds());
    }
}
