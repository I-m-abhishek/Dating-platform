package com.dating.platform.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "UpsertPromptAnswerRequest")
public record UpsertPromptAnswerRequest(

        @NotNull(message = "Pick a prompt")
        UUID promptId,

        @NotBlank(message = "Write an answer")
        @Size(max = 300, message = "Answers are limited to 300 characters")
        String answer,

        int displayOrder
) {
}
