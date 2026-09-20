package com.dating.platform.common.exception;

public class PremiumRequiredException extends BusinessException {

    public PremiumRequiredException(String feature, String requiredTier) {
        super(ErrorCode.PREMIUM_REQUIRED,
                feature + " is available on the " + requiredTier + " plan",
                new PremiumContext(feature, requiredTier));
    }

    public record PremiumContext(String feature, String requiredTier) {
    }
}
