package com.dating.platform.subscription.entity;

import lombok.Getter;

/**
 * Every gated capability, with the tier that unlocks it.
 *
 * <p>Adding a paywall means adding a constant here and calling
 * {@code entitlementService.require(userId, Feature.X)} - never an inline tier check.
 */
@Getter
public enum Feature {

    /** See the Likes You grid unblurred. */
    SEE_WHO_LIKES_YOU(PlanTier.PLUS, "See who likes you"),

    /** Extra photo comments beyond the free daily allowance. */
    EXTRA_PHOTO_COMMENTS(PlanTier.PLUS, "More photo comments"),

    /** Auto-match every day instead of once a week. */
    DAILY_AUTO_MATCH(PlanTier.PREMIUM, "A new match every day"),

    /** Undo the last pass. */
    REWIND(PlanTier.PLUS, "Rewind your last pass"),

    /** Ignore the distance filter. */
    GLOBAL_MODE(PlanTier.PREMIUM, "Match anywhere in the world"),

    /** Browse without appearing in other people's discovery. */
    INCOGNITO(PlanTier.PREMIUM, "Browse incognito"),

    /** Read receipts in chat. */
    READ_RECEIPTS(PlanTier.PLUS, "Read receipts"),

    /** Advanced discovery filters (height, intent, lifestyle). */
    ADVANCED_FILTERS(PlanTier.PLUS, "Advanced filters"),

    /** Unlimited likes per day. */
    UNLIMITED_LIKES(PlanTier.PREMIUM, "Unlimited likes");

    private final PlanTier requiredTier;
    private final String label;

    Feature(PlanTier requiredTier, String label) {
        this.requiredTier = requiredTier;
        this.label = label;
    }
}
