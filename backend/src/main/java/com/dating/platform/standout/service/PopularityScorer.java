package com.dating.platform.standout.service;

import com.dating.platform.profile.entity.Profile;
import com.dating.platform.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides who is "standing out" right now.
 *
 * <p>The naive version - rank by raw like count - produces a leaderboard of the same ten
 * people forever, which is boring for browsers and demoralising for everyone else. This
 * scorer fixes that with three deliberate choices:
 *
 * <ul>
 *   <li><b>Log-scaled popularity.</b> The 400th like is worth far less than the 4th, so a
 *       runaway favourite cannot dominate the shelf outright.</li>
 *   <li><b>A newcomer boost.</b> Accounts younger than {@link #NEWCOMER_WINDOW} get a
 *       decaying bonus, so someone who joined yesterday can appear before they have any
 *       likes at all. Without this the shelf is closed to new users.</li>
 *   <li><b>Effort and presence.</b> Profile completeness and recent activity both count,
 *       which keeps the shelf full of people who will actually answer.</li>
 * </ul>
 *
 * <p>Popularity is capped at 45% of the total so the other signals can outweigh it.
 */
@Component
public class PopularityScorer {

    private static final Duration NEWCOMER_WINDOW = Duration.ofDays(14);
    private static final Duration ACTIVE_WINDOW = Duration.ofDays(7);

    private static final double W_POPULARITY = 0.45;
    private static final double W_COMPLETENESS = 0.20;
    private static final double W_ACTIVITY = 0.20;
    private static final double W_NEWCOMER = 0.15;

    public Scored score(User user, Profile profile, long likesReceived, long superLikesReceived) {
        double popularity = popularityScore(likesReceived, superLikesReceived);
        double completeness = profile == null ? 0 : profile.getCompleteness();
        double activity = activityScore(user.getLastActiveAt());
        double newcomer = newcomerScore(user.getCreatedAt());

        double total = W_POPULARITY * popularity
                + W_COMPLETENESS * completeness
                + W_ACTIVITY * activity
                + W_NEWCOMER * newcomer;

        return new Scored(round(total), reasonFor(popularity, newcomer, activity), (int) likesReceived);
    }

    /** Super likes are worth three ordinary ones; the whole thing is log-scaled. */
    private double popularityScore(long likes, long superLikes) {
        double weighted = likes + (superLikes * 2d);
        if (weighted <= 0) {
            return 0d;
        }
        // log1p(x) / log1p(120) reaches 1.0 at roughly 120 weighted likes in the window
        return Math.min(1d, Math.log1p(weighted) / Math.log1p(120));
    }

    private double activityScore(Instant lastActiveAt) {
        if (lastActiveAt == null) {
            return 0d;
        }
        Duration since = Duration.between(lastActiveAt, Instant.now());
        if (since.isNegative()) {
            return 1d;
        }
        double ratio = (double) since.toHours() / ACTIVE_WINDOW.toHours();
        return Math.max(0d, 1d - ratio);
    }

    private double newcomerScore(Instant createdAt) {
        if (createdAt == null) {
            return 0d;
        }
        Duration age = Duration.between(createdAt, Instant.now());
        if (age.compareTo(NEWCOMER_WINDOW) >= 0) {
            return 0d;
        }
        return 1d - ((double) age.toHours() / NEWCOMER_WINDOW.toHours());
    }

    private String reasonFor(double popularity, double newcomer, double activity) {
        if (newcomer > 0.5) {
            return "NEW";
        }
        if (popularity > 0.6) {
            return "POPULAR";
        }
        if (activity > 0.7) {
            return "ACTIVE";
        }
        return "RISING";
    }

    private double round(double value) {
        return Math.round(Math.max(0d, Math.min(1d, value)) * 10000d) / 10000d;
    }

    public record Scored(double score, String reason, int likesReceived) {
    }
}
