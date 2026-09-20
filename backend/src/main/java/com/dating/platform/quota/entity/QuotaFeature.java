package com.dating.platform.quota.entity;

import lombok.Getter;

/**
 * Metered product allowances. Distinct from {@code Feature}, which is a boolean unlock:
 * these are counters that reset on a period boundary.
 */
@Getter
public enum QuotaFeature {

    PHOTO_COMMENT("photo comments", Period.DAILY),
    LIKE("likes", Period.DAILY),
    AUTO_MATCH_WEEKLY("weekly auto-matches", Period.WEEKLY),
    AUTO_MATCH_DAILY("daily auto-matches", Period.DAILY),
    REWIND("rewinds", Period.DAILY);

    private final String label;
    private final Period period;

    QuotaFeature(String label, Period period) {
        this.label = label;
        this.period = period;
    }

    public enum Period {
        DAILY,
        WEEKLY
    }
}
