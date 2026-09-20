package com.dating.platform.common.exception;

import java.time.Instant;

/**
 * Thrown when a user runs out of a metered allowance (comments, likes, auto-matches).
 * The context block tells the client exactly what to render in the paywall.
 */
public class QuotaExceededException extends BusinessException {

    public QuotaExceededException(String feature, int limit, Instant resetsAt, String upgradeHint) {
        super(ErrorCode.QUOTA_EXCEEDED,
                "You have used all " + limit + " of your " + feature + " for now",
                new QuotaContext(feature, limit, resetsAt, upgradeHint));
    }

    public record QuotaContext(String feature, int limit, Instant resetsAt, String upgradeHint) {
    }
}
