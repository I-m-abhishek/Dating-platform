package com.dating.platform.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "PromptAnswerResponse")
public record PromptAnswerResponse(
        UUID id,
        UUID promptId,
        String prompt,
        String answer,
        int displayOrder
) {
}
