package com.dating.platform.interaction.dto;

import com.dating.platform.interaction.entity.LikeType;
import com.dating.platform.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the Likes You tab.
 *
 * <p>When {@code blurred} is true the server has already stripped the identifying fields -
 * {@code user} carries only the blurhash and coarse attributes. The client blurs the
 * placeholder for effect, but there is nothing behind it to reveal, which is the point:
 * a paywall enforced only in the UI is not a paywall.
 */
@Schema(name = "InboundLikeResponse")
public record InboundLikeResponse(
        UUID likeId,
        boolean blurred,
        UserSummaryResponse user,
        LikeType type,
        String note,
        UUID targetPhotoId,
        String targetPhotoUrl,
        boolean seen,
        Instant likedAt
) {
}
