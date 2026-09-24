package com.dating.platform.interaction.dto;

import com.dating.platform.interaction.entity.LikeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "LikeRequest")
public record LikeRequest(

        @NotNull(message = "Who are you liking?")
        UUID targetUserId,

        LikeType type,

        /**
         * Liking a specific photo or prompt - with a comment - makes the like far more likely
         * to be returned. At most one target; it must belong to the person being liked.
         */
        UUID targetPhotoId,

        UUID targetPromptAnswerId,

        /** The comment written under the photo or prompt. Becomes the first chat message on a match. */
        @Size(max = 200, message = "Keep your comment under 200 characters")
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

    @AssertTrue(message = "Like a photo or a prompt, not both at once")
    public boolean isSingleTarget() {
        return targetPhotoId == null || targetPromptAnswerId == null;
    }

    /** The comment, trimmed; blank means no comment. */
    public String noteOrNull() {
        return note == null || note.isBlank() ? null : note.strip();
    }

    public LikeType typeOrDefault() {
        return type == null ? LikeType.STANDARD : type;
    }
}
