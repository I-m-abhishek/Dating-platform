package com.dating.platform.chat.dto;

import com.dating.platform.chat.entity.MessageAttachment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "AttachmentResponse")
public record AttachmentResponse(
        UUID id,
        String url,
        String contentType,
        String fileName,
        long sizeBytes,
        Integer width,
        Integer height,
        Integer durationSeconds,
        String waveform,
        String thumbnailUrl
) {

    public static AttachmentResponse from(MessageAttachment a) {
        return new AttachmentResponse(a.getId(), a.getUrl(), a.getContentType(), a.getFileName(),
                a.getSizeBytes(), a.getWidth(), a.getHeight(), a.getDurationSeconds(),
                a.getWaveform(), a.getThumbnailUrl());
    }
}
