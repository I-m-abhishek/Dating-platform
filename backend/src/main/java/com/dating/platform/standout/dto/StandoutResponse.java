package com.dating.platform.standout.dto;

import com.dating.platform.profile.dto.PhotoResponse;
import com.dating.platform.profile.dto.PromptAnswerResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * One tile on the Standouts shelf. {@code reason} is shown as a badge
 * ("Popular this week", "New here") so the ranking is legible rather than mysterious.
 */
@Schema(name = "StandoutResponse")
public record StandoutResponse(
        UUID userId,
        String displayName,
        int age,
        String city,
        Integer distanceKm,
        String reason,
        String reasonLabel,
        int rank,
        double compatibilityScore,
        List<String> sharedInterests,
        List<PhotoResponse> photos,
        List<PromptAnswerResponse> prompts,
        boolean photoVerified
) {
}
