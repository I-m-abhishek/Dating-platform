package com.dating.platform.subscription.entity;

/**
 * Ordered by capability. Comparisons use {@link #atLeast(PlanTier)} rather than
 * {@code ordinal()} so inserting a tier later does not silently change behaviour.
 */
public enum PlanTier {
    FREE(0),
    PLUS(1),
    PREMIUM(2);

    private final int rank;

    PlanTier(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(PlanTier other) {
        return this.rank >= other.rank;
    }

    public int rank() {
        return rank;
    }
}
