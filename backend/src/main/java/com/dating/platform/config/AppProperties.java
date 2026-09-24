package com.dating.platform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Every tunable knob of the platform, bound once and injected as a typed object.
 * Nothing in the codebase reads {@code @Value} strings directly.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Cors cors,
        @NotNull Jwt jwt,
        @NotNull Storage storage,
        @NotNull Matching matching,
        @NotNull Quota quota,
        @NotNull Chat chat,
        @NotNull RateLimit rateLimit,
        @DefaultValue("false") boolean seedDemoData
) {

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            @DefaultValue("true") boolean allowCredentials,
            @DefaultValue("3600") long maxAge
    ) {
    }

    public record Jwt(
            @NotBlank String secret,
            @DefaultValue("dating-platform") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("30d") Duration refreshTokenTtl
    ) {
    }

    public record Storage(
            @DefaultValue("local") String provider,
            @DefaultValue("./storage") String localRoot,
            @NotBlank String publicBaseUrl,
            List<String> allowedImageTypes,
            List<String> allowedAttachmentTypes,
            @DefaultValue("10485760") long maxImageBytes,
            @DefaultValue("26214400") long maxAttachmentBytes
    ) {
    }

    public record Matching(
            @DefaultValue("0 0 9 * * MON") String weeklyAutoMatchCron,
            @DefaultValue("0 0 9 * * *") String dailyAutoMatchCron,
            @DefaultValue("0 0 */6 * * *") String standoutRefreshCron,
            @DefaultValue("400") int candidatePoolSize,
            @DefaultValue("80") int maxDistanceKmDefault,
            @DefaultValue("0.35") double minCompatibilityScore
    ) {
    }

    public record Quota(Tier free, Tier plus, Tier premium) {

        /** {@code -1} means unlimited; {@code null} means the tier does not grant the feature. */
        public record Tier(
                @DefaultValue("5") int photoCommentsPerDay,
                @DefaultValue("20") int likesPerDay,
                @DefaultValue("1") int superLikesPerDay,
                @DefaultValue("1") int autoMatchPerWeek,
                @DefaultValue("0") int autoMatchPerDay,
                @DefaultValue("0") int rewindsPerDay
        ) {
        }
    }

    public record Chat(
            @DefaultValue("3") int openerMessageLimit,
            @DefaultValue("2000") int maxMessageLength
    ) {
    }

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("120") int defaultCapacity,
            @DefaultValue("1m") Duration defaultRefillPeriod
    ) {
    }
}
