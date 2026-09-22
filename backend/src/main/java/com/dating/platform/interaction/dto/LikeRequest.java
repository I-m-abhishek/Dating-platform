package com.dating.platform.interaction.dto;

import com.dating.platform.interaction.entity.LikeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "LikeRequest")
public record LikeRequest(

        @NotNull(message = "Who are you liking?")
        UUID targetUserId,

        LikeType type,

        /** Liking a specific photo makes the like far more likely to be returned. */
        UUID targetPhotoId,

        UUID targetPromptAnswerId,

        @Size(max = 200, message = "Keep your note under 200 characters")
        String note,

        /**
         * Client-generated idempotency key, ideally a UUIDv7.
         *
         * <p>Mobile networks retry. Without this a retried like is indistinguishable from a
         * second like: it spends another of the day's allowance and can fire the match
         * notification twice. With it the server recognises the retry and replays the
         * original outcome. Optional, so older clients keep working.
         */
        @Size(max = 64, message = "Idempotency key is too long")
        String clientLikeId
) {

    public LikeType typeOrDefault() {
        return type == null ? LikeType.STANDARD : type;
    }
}
