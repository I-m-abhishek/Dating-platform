package com.dating.platform.discovery.dto;

import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Home feed filters. Anything left {@code null} falls back to the user's saved preferences,
 * so the client can send a partial filter without restating the defaults.
 *
 * <p>Fields marked "advanced" require {@code Feature.ADVANCED_FILTERS}; the service rejects
 * them for free accounts rather than silently ignoring them, so the UI can show the upsell.
 */
@Schema(name = "FeedFilterRequest")
public record FeedFilterRequest(

        @Min(value = 18, message = "Minimum age is 18")
        @Max(value = 120, message = "Maximum age is 120")
        Integer minAge,

        @Min(value = 18, message = "Minimum age is 18")
        @Max(value = 120, message = "Maximum age is 120")
        Integer maxAge,

        @Min(value = 1, message = "Distance must be at least 1 km")
        @Max(value = 20000, message = "Distance must be at most 20000 km")
        Integer maxDistanceKm,

        @Size(max = 4)
        Set<Gender> genders,

        /** advanced */
        Set<RelationshipIntent> intents,

        /** advanced */
        @Min(120) @Max(250) Integer minHeightCm,

        /** advanced */
        @Min(120) @Max(250) Integer maxHeightCm,

        /** advanced */
        Boolean onlyVerified,

        /** advanced */
        @Size(max = 6) Set<UUID> interestIds,

        SortOrder sort
) {

    public enum SortOrder {
        /** Compatibility first - the default. */
        RECOMMENDED,
        NEWEST,
        NEAREST,
        RECENTLY_ACTIVE
    }

    @AssertTrue(message = "Minimum age cannot be greater than maximum age")
    public boolean isAgeRangeValid() {
        return minAge == null || maxAge == null || minAge <= maxAge;
    }

    @AssertTrue(message = "Minimum height cannot be greater than maximum height")
    public boolean isHeightRangeValid() {
        return minHeightCm == null || maxHeightCm == null || minHeightCm <= maxHeightCm;
    }

    public boolean usesAdvancedFilters() {
        return (intents != null && !intents.isEmpty())
                || minHeightCm != null
                || maxHeightCm != null
                || Boolean.TRUE.equals(onlyVerified)
                || (interestIds != null && !interestIds.isEmpty());
    }

    public SortOrder sortOrDefault() {
        return sort == null ? SortOrder.RECOMMENDED : sort;
    }

    public static FeedFilterRequest empty() {
        return new FeedFilterRequest(null, null, null, null, null, null, null, null, null, null);
    }
}
