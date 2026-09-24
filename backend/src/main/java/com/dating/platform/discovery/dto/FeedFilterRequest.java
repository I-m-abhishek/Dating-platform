package com.dating.platform.discovery.dto;

import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.LifestyleChoice;
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
 * <p>The free / paid split follows the major apps: who, how old, how far and shared
 * interests are free, in the recommended order; intent, height, activity, family plans,
 * habits, verification and every other sort order are "advanced" and require
 * {@code Feature.ADVANCED_FILTERS}. Sorting counts because "Active now" or "Nearest" would
 * otherwise hand out the paid activity and distance-first views for free.
 *
 * <p>The service rejects advanced fields for free accounts rather than silently ignoring
 * them, so the UI can upsell.
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

        /** "Show me" - the same setting as the profile's interested-in. */
        @Size(max = 4)
        Set<Gender> genders,

        /** Free: show people who share at least one of these interests. */
        @Size(max = 6) Set<UUID> interestIds,

        /** advanced - "looking for" */
        Set<RelationshipIntent> intents,

        /** advanced */
        @Min(120) @Max(250) Integer minHeightCm,

        /** advanced */
        @Min(120) @Max(250) Integer maxHeightCm,

        /** advanced */
        Boolean onlyVerified,

        /** advanced - only people active within this many hours (24 = today, 168 = this week) */
        @Min(1) @Max(720) Integer activeWithinHours,

        /** advanced - family plans */
        Set<ChildrenPreference> children,

        /** advanced */
        Set<LifestyleChoice> drinking,

        /** advanced */
        Set<LifestyleChoice> smoking,

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
        return notEmpty(intents)
                || minHeightCm != null
                || maxHeightCm != null
                || Boolean.TRUE.equals(onlyVerified)
                || activeWithinHours != null
                || notEmpty(children)
                || notEmpty(drinking)
                || notEmpty(smoking)
                || sortOrDefault() != SortOrder.RECOMMENDED;
    }

    /** The same filter with every paid field cleared - what a free account may use. */
    public FeedFilterRequest withoutAdvanced() {
        return new FeedFilterRequest(minAge, maxAge, maxDistanceKm, genders, interestIds,
                null, null, null, null, null, null, null, null, SortOrder.RECOMMENDED);
    }

    /** The same filter with the preference-backed fields replaced. */
    public FeedFilterRequest withPreferences(Integer minAge, Integer maxAge, Integer maxDistanceKm,
                                             Set<Gender> genders) {
        return new FeedFilterRequest(minAge, maxAge, maxDistanceKm, genders, interestIds,
                intents, minHeightCm, maxHeightCm, onlyVerified, activeWithinHours,
                children, drinking, smoking, sort);
    }

    public SortOrder sortOrDefault() {
        return sort == null ? SortOrder.RECOMMENDED : sort;
    }

    public static FeedFilterRequest empty() {
        return new FeedFilterRequest(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static boolean notEmpty(Set<?> set) {
        return set != null && !set.isEmpty();
    }
}
