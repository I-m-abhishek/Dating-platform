package com.dating.platform.user.dto;

import com.dating.platform.subscription.entity.PlanTier;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** The caller's own account. Everything here is private to them. */
@Schema(name = "AccountResponse")
public record AccountResponse(
        UUID id,
        String email,
        String displayName,
        LocalDate dateOfBirth,
        int age,
        Gender gender,
        Set<Gender> interestedIn,
        UserStatus status,
        String city,
        String country,
        Double latitude,
        Double longitude,
        int preferredMinAge,
        int preferredMaxAge,
        int preferredMaxDistanceKm,
        boolean globalMode,
        boolean incognito,
        boolean photoVerified,
        boolean emailVerified,
        boolean onboardingCompleted,
        PlanTier tier,
        Instant lastActiveAt,
        Instant createdAt
) {
}
