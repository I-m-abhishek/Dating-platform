package com.dating.platform.interaction.dto;

import com.dating.platform.common.response.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Likes tab payload: the rows plus the counters the upsell banner needs
 * ("14 people like you - see them all").
 */
@Schema(name = "LikesOverviewResponse")
public record LikesOverviewResponse(
        long totalLikes,
        long newLikes,
        boolean revealed,
        String upgradeHint,
        PageResponse<InboundLikeResponse> likes
) {
}
