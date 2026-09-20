package com.dating.platform.interaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(name = "PassRequest")
public record PassRequest(

        @NotNull(message = "Who are you passing on?")
        UUID targetUserId
) {
}
