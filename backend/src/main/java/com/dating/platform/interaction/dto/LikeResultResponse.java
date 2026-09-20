package com.dating.platform.interaction.dto;

import com.dating.platform.match.dto.MatchResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Outcome of sending a like. {@code matched} is the moment the UI plays the
 * "It is a match" animation, so it must be authoritative and never inferred client side.
 */
@Schema(name = "LikeResultResponse")
public record LikeResultResponse(
        UUID likeId,
        boolean matched,
        MatchResponse match,
        int likesRemainingToday
) {
}
