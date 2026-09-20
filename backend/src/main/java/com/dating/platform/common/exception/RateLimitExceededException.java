package com.dating.platform.common.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED,
                "Too many requests. Try again in " + retryAfterSeconds + " seconds.",
                new RetryContext(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public record RetryContext(long retryAfterSeconds) {
    }
}
