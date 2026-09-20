package com.dating.platform.auth.dto;

import com.dating.platform.user.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Issued on register, login and refresh.
 *
 * <p>{@code onboardingRequired} lets the client route straight to the onboarding flow
 * instead of bouncing off a 428 on the first protected call.
 */
@Schema(name = "AuthResponse")
public record AuthResponse(
        UUID userId,
        String email,
        String displayName,
        UserStatus status,
        boolean onboardingRequired,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}
