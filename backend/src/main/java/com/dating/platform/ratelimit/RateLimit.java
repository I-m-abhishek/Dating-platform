package com.dating.platform.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Declarative per-caller rate limit on a controller method.
 *
 * <p>Example: three login attempts a minute per IP.
 * <pre>{@code
 * @RateLimit(name = "auth.login", capacity = 5, period = 1, unit = TimeUnit.MINUTES, scope = Scope.IP)
 * }</pre>
 *
 * <p>This is protection against abuse and scraping. It is NOT the product quota system
 * (5 comments a day, weekly auto-match) - that lives in {@code quota} and is persisted.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Logical bucket name. Requests with the same name + caller share a bucket. */
    String name();

    /** Maximum burst. */
    int capacity();

    /** Refill window length. */
    long period() default 1;

    TimeUnit unit() default TimeUnit.MINUTES;

    Scope scope() default Scope.USER_OR_IP;

    enum Scope {
        /** Authenticated user id, falling back to remote address for anonymous calls. */
        USER_OR_IP,
        /** Always the remote address - use for pre-auth endpoints. */
        IP,
        /** One shared bucket for the whole application - use to protect a fragile dependency. */
        GLOBAL
    }
}
