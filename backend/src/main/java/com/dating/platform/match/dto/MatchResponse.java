package com.dating.platform.match.dto;

import com.dating.platform.match.entity.MatchSource;
import com.dating.platform.match.entity.MatchStatus;
import com.dating.platform.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "MatchResponse")
public record MatchResponse(
        UUID id,
        UserSummaryResponse user,
        MatchSource source,
        MatchStatus status,
        Instant matchedAt,
        Double compatibilityScore,
        List<String> highlights,
        UUID conversationId,
        boolean conversationStarted,
        String lastMessagePreview,
        Instant lastMessageAt,
        int unreadCount,
        /** No messages yet - the UI shows these in the "New matches" rail. */
        boolean isNew
) {
}
