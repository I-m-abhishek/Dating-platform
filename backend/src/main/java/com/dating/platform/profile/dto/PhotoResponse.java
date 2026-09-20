package com.dating.platform.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "PhotoResponse")
public record PhotoResponse(
        UUID id,
        String url,
        String blurhash,
        Integer width,
        Integer height,
        String caption,
        int displayOrder,
        boolean primaryPhoto,
        int commentCount,
        int likeCount
) {
}
