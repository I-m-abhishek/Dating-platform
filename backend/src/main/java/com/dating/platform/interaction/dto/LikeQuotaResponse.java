package com.dating.platform.interaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Today's like and super like allowance. {@code -1} means unlimited. */
@Schema(name = "LikeQuotaResponse")
public record LikeQuotaResponse(
        int likesLimit,
        int likesRemaining,
        int superLikesLimit,
        int superLikesRemaining,
        Instant resetsAt
) {
}
