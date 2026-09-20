package com.dating.platform.ratelimit;

import com.dating.platform.common.exception.RateLimitExceededException;
import com.dating.platform.config.AppProperties;
import com.dating.platform.security.SecurityUtils;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Applies {@link RateLimit} annotations, and a coarse default limit to every other
 * API call so an un-annotated endpoint is never completely unprotected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final AppProperties appProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AppProperties.RateLimit defaults = appProperties.rateLimit();
        if (!defaults.enabled() || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RateLimit.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RateLimit.class);
        }

        String name = annotation != null ? annotation.name() : "default";
        int capacity = annotation != null ? annotation.capacity() : defaults.defaultCapacity();
        Duration period = annotation != null
                ? Duration.ofMillis(annotation.unit().toMillis(annotation.period()))
                : defaults.defaultRefillPeriod();
        RateLimit.Scope scope = annotation != null ? annotation.scope() : RateLimit.Scope.USER_OR_IP;

        String bucketKey = name + ":" + callerKey(scope, request);
        ConsumptionProbe probe = rateLimitService.tryConsume(bucketKey, capacity, period);

        response.addHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(probe.getRemainingTokens(), 0)));

        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            response.addHeader("X-RateLimit-Reset", String.valueOf(retryAfter));
            log.warn("Rate limit hit on bucket {} ({} req / {})", bucketKey, capacity, period);
            throw new RateLimitExceededException(retryAfter);
        }
        return true;
    }

    private String callerKey(RateLimit.Scope scope, HttpServletRequest request) {
        return switch (scope) {
            case GLOBAL -> "global";
            case IP -> clientIp(request);
            case USER_OR_IP -> SecurityUtils.currentUserIdOrEmpty()
                    .map(id -> "u:" + id)
                    .orElseGet(() -> clientIp(request));
        };
    }

    /** Honours a single reverse proxy hop; the proxy must strip client supplied headers. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return "ip:" + (comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim());
        }
        return "ip:" + request.getRemoteAddr();
    }
}
