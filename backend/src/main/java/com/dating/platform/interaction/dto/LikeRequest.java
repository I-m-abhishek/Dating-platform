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
        String note
) {

    public LikeType typeOrDefault() {
        return type == null ? LikeType.STANDARD : type;
    }
}
