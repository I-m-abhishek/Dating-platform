package com.dating.platform.auth.service;

import com.dating.platform.auth.dto.AuthResponse;
import com.dating.platform.auth.dto.ChangePasswordRequest;
import com.dating.platform.auth.dto.LoginRequest;
import com.dating.platform.auth.dto.RegisterRequest;
import com.dating.platform.auth.entity.RefreshToken;
import com.dating.platform.auth.repository.RefreshTokenRepository;
import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.config.AppProperties;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.security.JwtTokenProvider;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.Role;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registration, login, token refresh and password change.
 *
 * <p>Token model: a short-lived stateless JWT for access, plus an opaque, rotating,
 * database-backed refresh token. Rotation with family revocation means a stolen refresh
 * token is usable at most once before the theft is detected and the whole session family
 * is killed.
 *
 * <p>Login never distinguishes "no such account" from "wrong password" - both produce
 * {@code INVALID_CREDENTIALS}, so the endpoint cannot be used to enumerate accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 48;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AppProperties appProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent, String ip) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .interestedIn(EnumSet.copyOf(request.interestedIn()))
                .status(UserStatus.PENDING_ONBOARDING)
                .roles(EnumSet.of(Role.USER))
                .lastActiveAt(Instant.now())
                .build());

        // Every user has exactly one profile row from the moment they exist, so no read path
        // ever has to cope with its absence.
        profileRepository.save(Profile.builder().user(user).build());

        log.info("Registered user {}", user.getId());
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String ip) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.debug("Failed login attempt for {}", user.getId());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        assertCanAuthenticate(user);

        user.setLastActiveAt(Instant.now());
        if (user.getStatus() == UserStatus.PAUSED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        return issueTokens(user, userAgent, ip);
    }

    /**
     * Rotates a refresh token.
     *
     * <p>Presenting an already-revoked token is treated as a replay: the whole family is
     * revoked, which logs out the attacker and the legitimate user, who can log in again.
     */
    @Transactional
    public AuthResponse refresh(String presentedToken, String userAgent, String ip) {
        String hash = hash(presentedToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (stored.isRevoked()) {
            log.warn("Refresh token replay detected for user {} - revoking family {}",
                    stored.getUserId(), stored.getFamilyId());
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), Instant.now());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!stored.isUsable()) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", stored.getUserId()));
        assertCanAuthenticate(user);

        stored.setRevoked(true);
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        return issueTokens(user, userAgent, ip, stored.getFamilyId());
    }

    @Transactional
    public void logout(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Your current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Changing a password invalidates every other session - that is the point of doing it.
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Password changed for user {}", userId);
    }

    // ---- internals -----------------------------------------------------

    private AuthResponse issueTokens(User user, String userAgent, String ip) {
        return issueTokens(user, userAgent, ip, UUID.randomUUID());
    }

    private AuthResponse issueTokens(User user, String userAgent, String ip, UUID familyId) {
        Set<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(), roles);

        String refreshToken = generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(refreshToken))
                .familyId(familyId)
                .expiresAt(Instant.now().plus(appProperties.jwt().refreshTokenTtl()))
                .userAgent(truncate(userAgent, 250))
                .ipAddress(truncate(ip, 60))
                .build());

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getOnboardingCompletedAt() == null,
                accessToken,
                refreshToken,
                "Bearer",
                tokenProvider.accessTokenTtlSeconds());
    }

    private void assertCanAuthenticate(User user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 is right here: the input is already high entropy, so a slow KDF buys nothing. */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
