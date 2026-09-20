package com.dating.platform.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Shared shape for interests and qualities - the UI renders both as chips. */
@Schema(name = "TagResponse")
public record TagResponse(UUID id, String slug, String label, String group, String emoji) {
}
