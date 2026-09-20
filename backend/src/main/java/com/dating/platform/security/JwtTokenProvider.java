package com.dating.platform.security;

import com.dating.platform.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and verifies stateless access tokens (HS256).
 *
 * <p>Access tokens are short lived (15 min by default) and carry only what the
 * filter needs: subject, email, roles. Refresh tokens are opaque and stored
 * hashed in the database so they can be revoked.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final AppProperties.Jwt config;

    public JwtTokenProvider(AppProperties properties) {
        this.config = properties.jwt();
        this.key = Keys.hmacShaKeyFor(decodeSecret(config.secret()));
    }

    public String createAccessToken(UUID userId, String email, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(config.accessTokenTtl());
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(config.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .claims(Map.of(CLAIM_EMAIL, email, CLAIM_ROLES, roles, CLAIM_TYPE, TYPE_ACCESS))
                .signWith(key)
                .compact();
    }

    public Instant accessTokenExpiry() {
        return Instant.now().plus(config.accessTokenTtl());
    }

    public long accessTokenTtlSeconds() {
        return config.accessTokenTtl().toSeconds();
    }

    /** @return parsed claims, or {@code null} when the token is absent, malformed or expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Rejected expired token");
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected invalid token: {}", e.getMessage());
            return null;
        }
    }

    public UUID userIdFrom(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String emailFrom(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> rolesFrom(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    private static byte[] decodeSecret(String secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64, fall through to raw bytes
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        return raw;
    }
}
