package com.dating.platform.user.dto;

import com.dating.platform.user.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "UpdatePreferencesRequest")
public record UpdatePreferencesRequest(

        @Min(18) @Max(120) Integer preferredMinAge,
        @Min(18) @Max(120) Integer preferredMaxAge,
        @Min(1) @Max(20000) Integer preferredMaxDistanceKm,

        @Size(max = 4) Set<Gender> interestedIn,

        /** Requires Feature.GLOBAL_MODE. */
        Boolean globalMode,

        /** Requires Feature.INCOGNITO. */
        Boolean incognito
) {

    @AssertTrue(message = "Minimum age cannot be greater than maximum age")
    public boolean isAgeRangeValid() {
        return preferredMinAge == null || preferredMaxAge == null || preferredMinAge <= preferredMaxAge;
    }
}
