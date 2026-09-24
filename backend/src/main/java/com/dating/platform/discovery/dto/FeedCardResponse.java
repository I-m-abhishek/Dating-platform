package com.dating.platform.discovery.dto;

import com.dating.platform.profile.dto.PhotoResponse;
import com.dating.platform.profile.dto.PromptAnswerResponse;
import com.dating.platform.profile.dto.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * A card in the home feed. Carries enough to render the whole profile without a second
 * request - the feed is swiped fast and a per-card fetch would feel broken.
 */
@Schema(name = "FeedCardResponse")
public record FeedCardResponse(
        UUID userId,
        String displayName,
        int age,
        String city,
        Integer distanceKm,
        String bio,
        String jobTitle,
        String school,
        Integer heightCm,
        List<PhotoResponse> photos,
        List<PromptAnswerResponse> prompts,
        List<TagResponse> interests,
        List<TagResponse> qualities,
        List<String> sharedInterests,
        double compatibilityScore,
        List<String> highlights,
        boolean photoVerified,
        boolean recentlyActive,
        /** They super liked the viewer - shown with a badge and moved to the front of the deck. */
        boolean superLikedYou
) {
}
