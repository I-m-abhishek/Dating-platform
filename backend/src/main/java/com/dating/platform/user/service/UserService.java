package com.dating.platform.user.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.profile.service.ProfileCompletenessCalculator;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.service.EntitlementService;
import com.dating.platform.user.dto.AccountResponse;
import com.dating.platform.user.dto.UpdateLocationRequest;
import com.dating.platform.user.dto.UpdatePreferencesRequest;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Account level operations: preferences, location, onboarding, pause and deactivate.
 *
 * <p>Profile <em>content</em> belongs to {@code ProfileService}; this service owns the
 * account itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** Below this, a profile is too thin to be shown to anyone. */
    private static final double MIN_COMPLETENESS_TO_ACTIVATE = 0.30;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PhotoRepository photoRepository;
    private final PromptAnswerRepository promptAnswerRepository;
    private final EntitlementService entitlementService;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID userId) {
        User user = requireUser(userId);
        return new AccountResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getDateOfBirth(),
                DateUtils.ageOf(user.getDateOfBirth()), user.getGender(), user.getInterestedIn(),
                user.getStatus(), user.getCity(), user.getCountry(), user.getLatitude(), user.getLongitude(),
                user.getPreferredMinAge(), user.getPreferredMaxAge(), user.getPreferredMaxDistanceKm(),
                user.isGlobalMode(), user.isIncognito(), user.isPhotoVerified(), user.isEmailVerified(),
                user.getOnboardingCompletedAt() != null, entitlementService.tierOf(userId),
                user.getLastActiveAt(), user.getCreatedAt());
    }

    @Transactional
    public AccountResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        User user = requireUser(userId);

        if (request.preferredMinAge() != null) {
            user.setPreferredMinAge(request.preferredMinAge());
        }
        if (request.preferredMaxAge() != null) {
            user.setPreferredMaxAge(request.preferredMaxAge());
        }
        if (request.preferredMaxDistanceKm() != null) {
            user.setPreferredMaxDistanceKm(request.preferredMaxDistanceKm());
        }
        if (request.interestedIn() != null && !request.interestedIn().isEmpty()) {
            user.setInterestedIn(EnumSet.copyOf(request.interestedIn()));
        }
        if (Boolean.TRUE.equals(request.globalMode())) {
            entitlementService.require(userId, Feature.GLOBAL_MODE);
            user.setGlobalMode(true);
        } else if (Boolean.FALSE.equals(request.globalMode())) {
            user.setGlobalMode(false);
        }
        if (Boolean.TRUE.equals(request.incognito())) {
            entitlementService.require(userId, Feature.INCOGNITO);
            user.setIncognito(true);
        } else if (Boolean.FALSE.equals(request.incognito())) {
            user.setIncognito(false);
        }

        userRepository.save(user);
        return getAccount(userId);
    }

    /**
     * Stores the user's position.
     *
     * <p>Coordinates are kept at full precision for distance maths but never leave the
     * server: every response exposes a rounded distance, not a location.
     */
    @Transactional
    public AccountResponse updateLocation(UUID userId, UpdateLocationRequest request) {
        User user = requireUser(userId);
        user.setLatitude(request.latitude());
        user.setLongitude(request.longitude());
        user.setCity(request.city());
        user.setCountry(request.country());
        userRepository.save(user);
        return getAccount(userId);
    }

    /**
     * Finishes onboarding and makes the account discoverable.
     * Refuses while the profile is too thin - an empty profile in the feed hurts everyone.
     */
    @Transactional
    public AccountResponse completeOnboarding(UUID userId) {
        User user = requireUser(userId);
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));

        long photos = photoRepository.countByUserId(userId);
        if (photos == 0) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE, "Add at least one photo to continue");
        }
        if (user.getLatitude() == null) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE, "Share your location to continue");
        }

        double completeness = ProfileCompletenessCalculator.calculate(user, profile, photos,
                promptAnswerRepository.countByUserId(userId));
        profile.setCompleteness(completeness);
        profileRepository.save(profile);

        if (completeness < MIN_COMPLETENESS_TO_ACTIVATE) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE,
                    "Add a little more to your profile before you start matching");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setOnboardingCompletedAt(Instant.now());
        userRepository.save(user);

        log.info("User {} completed onboarding (completeness {})", userId, completeness);
        return getAccount(userId);
    }

    /** Hides the profile without deleting anything. Reversed automatically on next login. */
    @Transactional
    public void pause(UUID userId) {
        User user = requireUser(userId);
        user.setStatus(UserStatus.PAUSED);
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(UUID userId) {
        User user = requireUser(userId);
        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);
        log.info("User {} deactivated their account", userId);
    }

    /** Cheap heartbeat called by the client; drives the "active recently" badge. */
    @Transactional
    public void touchActivity(UUID userId) {
        userRepository.touchLastActive(userId, Instant.now());
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
