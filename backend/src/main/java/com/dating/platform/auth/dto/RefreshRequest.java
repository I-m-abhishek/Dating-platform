package com.dating.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest")
public record RefreshRequest(@NotBlank(message = "Refresh token is required") String refreshToken) {
}
