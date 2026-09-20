package com.dating.platform.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "BlockRequest")
public record BlockRequest(

        @NotNull(message = "Who do you want to block?")
        UUID userId,

        @Size(max = 200) String reason
) {
}
