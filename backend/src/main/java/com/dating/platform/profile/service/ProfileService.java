package com.dating.platform.profile.service;

import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ForbiddenException;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.interaction.entity.LikeStatus;
import com.dating.platform.interaction.repository.LikeRepository;
import com.dating.platform.match.engine.CompatibilityScorer;
import com.dating.platform.match.entity.Match;
import com.dating.platform.match.entity.MatchStatus;
import com.dating.platform.match.repository.MatchRepository;
import com.dating.platform.profile.dto.ProfileResponse;
import com.dating.platform.profile.dto.PublicProfileResponse;
import com.dating.platform.profile.dto.UpdateProfileRequest;
import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.entity.Quality;
import com.dating.platform.profile.mapper.ProfileMapper;
import com.dating.platform.profile.repository.InterestRepository;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.profile.repository.QualityRepository;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the profile aggregate: reading it, editing it, and deciding what a viewer may see.
 *
 * <p>Transaction boundaries live here - controllers never open one, repositories never
 * contain business rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PhotoRepository photoRepository;
    private final PromptAnswerRepository promptAnswerRepository;
    private final InterestRepository interestRepository;
    private final QualityRepository qualityRepository;
    private final ProfileMapper profileMapper;
    private final BlockService blockService;
    private final CompatibilityScorer compatibilityScorer;
    private final MatchRepository matchRepository;
    private final LikeRepository likeRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getOwnProfile(UUID userId) {
        User user = requireUser(userId);
        Profile profile = requireProfile(userId);
        List<Photo> photos = photoRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
        List<PromptAnswer> prompts = promptAnswerRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
        return profileMapper.toOwnProfile(user, profile, photos, prompts);
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID viewerId, UUID targetUserId) {
        if (blockService.isBlockedEitherWay(viewerId, targetUserId)) {
            throw new ForbiddenException(ErrorCode.USER_BLOCKED, ErrorCode.USER_BLOCKED.getDefaultMessage());
        }

        User target = requireUser(targetUserId);
        if (target.getStatus() == UserStatus.BANNED || target.getStatus() == UserStatus.DEACTIVATED) {
            throw new ResourceNotFoundException("Profile", targetUserId);
        }

        User viewer = requireUser(viewerId);
        Profile targetProfile = requireProfile(targetUserId);
        Profile viewerProfile = profileRepository.findByUserId(viewerId).orElse(null);

        List<Photo> photos = photoRepository.findAllByUserIdOrderByDisplayOrderAsc(targetUserId);
        List<PromptAnswer> prompts = promptAnswerRepository.findAllByUserIdOrderByDisplayOrderAsc(targetUserId);

        Integer distanceKm = distanceBetween(viewer, target);
        Double score = viewerProfile == null ? null
                : compatibilityScorer.score(viewer, viewerProfile, target, targetProfile).total();
        List<String> shared = viewerProfile == null ? List.of()
                : compatibilityScorer.sharedInterestLabels(viewerProfile, targetProfile);

        return profileMapper.toPublicProfile(target, targetProfile, photos, prompts, distanceKm, score, shared,
                relationshipBetween(viewerId, targetUserId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        Profile profile = requireProfile(userId);

        applyTextFields(profile, request);
        applyEnumFields(profile, request);

        if (request.languages() != null) {
            profile.setLanguages(new LinkedHashSet<>(request.languages()));
        }
        if (request.interestIds() != null) {
            profile.setInterests(resolveInterests(request.interestIds()));
        }
        if (request.qualityIds() != null) {
            profile.setQualities(resolveQualities(request.qualityIds()));
        }

        profile.setCompleteness(ProfileCompletenessCalculator.calculate(user, profile,
                photoRepository.countByUserId(userId),
                promptAnswerRepository.countByUserId(userId)));
        profileRepository.save(profile);

        log.debug("Profile updated for user {} (completeness {})", userId, profile.getCompleteness());
        return getOwnProfile(userId);
    }

    /** Recomputed whenever photos or prompts change, so the score never goes stale. */
    @Transactional
    public void recalculateCompleteness(UUID userId) {
        User user = requireUser(userId);
        Profile profile = requireProfile(userId);
        profile.setCompleteness(ProfileCompletenessCalculator.calculate(user, profile,
                photoRepository.countByUserId(userId),
                promptAnswerRepository.countByUserId(userId)));
        profileRepository.save(profile);
    }

    private void applyTextFields(Profile profile, UpdateProfileRequest request) {
        if (request.bio() != null) {
            profile.setBio(blankToNull(request.bio()));
        }
        if (request.jobTitle() != null) {
            profile.setJobTitle(blankToNull(request.jobTitle()));
        }
        if (request.company() != null) {
            profile.setCompany(blankToNull(request.company()));
        }
        if (request.school() != null) {
            profile.setSchool(blankToNull(request.school()));
        }
        if (request.educationLevel() != null) {
            profile.setEducationLevel(blankToNull(request.educationLevel()));
        }
        if (request.hometown() != null) {
            profile.setHometown(blankToNull(request.hometown()));
        }
        if (request.religion() != null) {
            profile.setReligion(blankToNull(request.religion()));
        }
        if (request.politics() != null) {
            profile.setPolitics(blankToNull(request.politics()));
        }
        if (request.zodiacSign() != null) {
            profile.setZodiacSign(blankToNull(request.zodiacSign()));
        }
        if (request.heightCm() != null) {
            profile.setHeightCm(request.heightCm());
        }
    }

    private void applyEnumFields(Profile profile, UpdateProfileRequest request) {
        if (request.relationshipIntent() != null) {
            profile.setRelationshipIntent(request.relationshipIntent());
        }
        if (request.drinking() != null) {
            profile.setDrinking(request.drinking());
        }
        if (request.smoking() != null) {
            profile.setSmoking(request.smoking());
        }
        if (request.cannabis() != null) {
            profile.setCannabis(request.cannabis());
        }
        if (request.exercise() != null) {
            profile.setExercise(request.exercise());
        }
        if (request.children() != null) {
            profile.setChildren(request.children());
        }
    }

    private Set<Interest> resolveInterests(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Interest> found = interestRepository.findAllByIdIn(ids);
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more interests do not exist");
        }
        return new LinkedHashSet<>(found);
    }

    private Set<Quality> resolveQualities(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Quality> found = qualityRepository.findAllByIdIn(ids);
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more qualities do not exist");
        }
        return new LinkedHashSet<>(found);
    }

    /**
     * What the viewer may do with this profile.
     *
     * <p>The profile screen needs this to choose between "Like" and "Message". Deciding it
     * on the client from the matches list would be wrong the moment the list is stale - and
     * liking someone you already matched with is exactly the kind of thing users notice.
     */
    private PublicProfileResponse.Relationship relationshipBetween(UUID viewerId, UUID targetUserId) {
        Match match = matchRepository.findByPair(viewerId, targetUserId)
                .filter(m -> m.getStatus() == MatchStatus.ACTIVE)
                .orElse(null);

        if (match != null) {
            return new PublicProfileResponse.Relationship(
                    true, match.getId(), match.getConversationId(), match.getMatchedAt(), false);
        }

        boolean likeSent = likeRepository.findBySenderIdAndReceiverId(viewerId, targetUserId)
                .filter(like -> like.getStatus() == LikeStatus.PENDING)
                .isPresent();

        return likeSent
                ? PublicProfileResponse.Relationship.pendingLike()
                : PublicProfileResponse.Relationship.none();
    }

    private Integer distanceBetween(User viewer, User target) {
        if (viewer.getLatitude() == null || target.getLatitude() == null) {
            return null;
        }
        return GeoUtils.displayDistanceKm(GeoUtils.distanceKm(
                viewer.getLatitude(), viewer.getLongitude(),
                target.getLatitude(), target.getLongitude()));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Profile requireProfile(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));
    }

    private String blankToNull(String value) {
        return value.isBlank() ? null : value.trim();
    }
}
