package com.dating.platform.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * In-memory token buckets.
 *
 * <p>Single-node by design. To scale horizontally, swap the Caffeine cache for
 * {@code bucket4j-redis} with the same {@link #tryConsume} signature - no caller changes.
 */
@Service
public class RateLimitService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(200_000)
            .build();

    /**
     * Consumes one token.
     *
     * @return the probe describing remaining tokens and, on failure, the wait time.
     */
    public ConsumptionProbe tryConsume(String bucketKey, int capacity, Duration refillPeriod) {
        Bucket bucket = buckets.get(bucketKey, k -> newBucket(capacity, refillPeriod));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    public void reset(String bucketKey) {
        buckets.invalidate(bucketKey);
    }

    private Bucket newBucket(int capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
