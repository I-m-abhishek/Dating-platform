package com.dating.platform.profile.dto;

import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Partial update: every field is optional, {@code null} means "leave unchanged".
 * To clear a value the client sends an empty string / empty set.
 */
@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequest(

        @Size(max = 500, message = "Bio cannot exceed 500 characters")
        String bio,

        @Size(max = 100) String jobTitle,
        @Size(max = 100) String company,
        @Size(max = 120) String school,
        @Size(max = 60) String educationLevel,
        @Size(max = 120) String hometown,

        @Min(value = 120, message = "Height must be at least 120 cm")
        @Max(value = 250, message = "Height must be at most 250 cm")
        Integer heightCm,

        @Size(max = 60) String religion,
        @Size(max = 60) String politics,
        @Size(max = 20) String zodiacSign,

        RelationshipIntent relationshipIntent,
        LifestyleChoice drinking,
        LifestyleChoice smoking,
        LifestyleChoice cannabis,
        LifestyleChoice exercise,
        ChildrenPreference children,

        @Size(max = 8, message = "Pick at most 8 languages")
        Set<@Size(max = 40) String> languages,

        @Size(max = 12, message = "Pick at most 12 interests")
        Set<UUID> interestIds,

        @Size(max = 6, message = "Pick at most 6 qualities")
        Set<UUID> qualityIds
) {
}
