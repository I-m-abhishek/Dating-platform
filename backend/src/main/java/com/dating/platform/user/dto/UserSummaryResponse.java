package com.dating.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * The compact card used by every list: matches, likes, standouts, conversations.
 *
 * <p>One shape for all of them on purpose - the frontend has a single {@code ProfileCard}
 * component, and a single DTO is what lets it stay single.
 */
@Schema(name = "UserSummary")
public record UserSummaryResponse(
        UUID userId,
        String displayName,
        int age,
        String city,
        Integer distanceKm,
        String primaryPhotoUrl,
        String blurhash,
        boolean photoVerified,
        boolean recentlyActive,
        Double compatibilityScore
) {
}
