package com.dating.platform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process Caffeine caches. Without {@link EnableCaching} every {@code @Cacheable} in the
 * codebase is inert, so it lives here beside the cache names it serves.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_PLANS = "plans";
    public static final String CACHE_REFERENCE_DATA = "referenceData";
    public static final String CACHE_STANDOUTS = "standouts";
    public static final String CACHE_ENTITLEMENTS = "entitlements";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_PLANS, CACHE_REFERENCE_DATA, CACHE_STANDOUTS, CACHE_ENTITLEMENTS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000));
        return manager;
    }
}
