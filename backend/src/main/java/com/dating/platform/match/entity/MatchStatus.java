package com.dating.platform.match.entity;

public enum MatchStatus {
    ACTIVE,
    /** One side unmatched. Kept, not deleted, so the pair is never re-suggested. */
    UNMATCHED,
    /** Nobody spoke inside the window - hidden from the Matches tab. */
    EXPIRED
}
