package com.dating.platform.profile.mapper;

import com.dating.platform.profile.dto.PhotoResponse;
import com.dating.platform.profile.dto.ProfileResponse;
import com.dating.platform.profile.dto.PromptAnswerResponse;
import com.dating.platform.profile.dto.PublicProfileResponse;
import com.dating.platform.profile.dto.TagResponse;
import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.entity.Quality;
import com.dating.platform.user.entity.User;
import com.dating.platform.util.DateUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Entity to DTO translation for the profile module.
 *
 * <p>Written by hand rather than generated: the public and private views differ by more
 * than field names, and the redaction rules are business logic worth reading.
 */
@Component
public class ProfileMapper {

    private static final Duration RECENTLY_ACTIVE_WINDOW = Duration.ofHours(72);

    public ProfileResponse toOwnProfile(User user,
                                        Profile profile,
                                        List<Photo> photos,
                                        List<PromptAnswer> prompts) {
        return new ProfileResponse(
                user.getId(),
                user.getDisplayName(),
                DateUtils.ageOf(user.getDateOfBirth()),
                user.getGender(),
                user.getInterestedIn(),
                user.getCity(),
                user.getCountry(),
                profile.getBio(),
                profile.getJobTitle(),
                profile.getCompany(),
                profile.getSchool(),
                profile.getEducationLevel(),
                profile.getHometown(),
                profile.getHeightCm(),
                profile.getReligion(),
                profile.getPolitics(),
                profile.getZodiacSign(),
                profile.getRelationshipIntent(),
                profile.getDrinking(),
                profile.getSmoking(),
                profile.getCannabis(),
                profile.getExercise(),
                profile.getChildren(),
                profile.getLanguages(),
                toInterestTags(profile.getInterests()),
                toQualityTags(profile.getQualities()),
                toPhotos(photos),
                toPrompts(prompts),
                profile.getCompleteness(),
                user.isPhotoVerified(),
                user.isIncognito());
    }

    public PublicProfileResponse toPublicProfile(User user,
                                                 Profile profile,
                                                 List<Photo> photos,
                                                 List<PromptAnswer> prompts,
                                                 Integer distanceKm,
                                                 Double compatibilityScore,
                                                 List<String> sharedInterests,
                                                 PublicProfileResponse.Relationship relationship) {
        boolean recentlyActive = user.getLastActiveAt() != null
                && Duration.between(user.getLastActiveAt(), Instant.now()).compareTo(RECENTLY_ACTIVE_WINDOW) < 0;

        return new PublicProfileResponse(
                user.getId(),
                user.getDisplayName(),
                DateUtils.ageOf(user.getDateOfBirth()),
                user.getGender(),
                user.getCity(),
                distanceKm,
                profile.getBio(),
                profile.getJobTitle(),
                profile.getCompany(),
                profile.getSchool(),
                profile.getHometown(),
                profile.getHeightCm(),
                profile.getReligion(),
                profile.getZodiacSign(),
                profile.getRelationshipIntent(),
                profile.getDrinking(),
                profile.getSmoking(),
                profile.getChildren(),
                profile.getLanguages(),
                toInterestTags(profile.getInterests()),
                toQualityTags(profile.getQualities()),
                toPhotos(photos),
                toPrompts(prompts),
                user.isPhotoVerified(),
                recentlyActive,
                // Deliberately coarse: exact last-seen timestamps are a safety problem.
                recentlyActive ? user.getLastActiveAt() : null,
                compatibilityScore,
                sharedInterests,
                relationship);
    }

    public PhotoResponse toPhoto(Photo photo) {
        return new PhotoResponse(
                photo.getId(),
                photo.getUrl(),
                photo.getBlurhash(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getCaption(),
                photo.getDisplayOrder(),
                photo.isPrimaryPhoto(),
                photo.getCommentCount(),
                photo.getLikeCount());
    }

    public List<PhotoResponse> toPhotos(List<Photo> photos) {
        return photos == null ? List.of() : photos.stream()
                .sorted(Comparator.comparingInt(Photo::getDisplayOrder))
                .map(this::toPhoto)
                .toList();
    }

    public PromptAnswerResponse toPrompt(PromptAnswer answer) {
        return new PromptAnswerResponse(
                answer.getId(),
                answer.getPrompt().getId(),
                answer.getPrompt().getText(),
                answer.getAnswer(),
                answer.getDisplayOrder());
    }

    public List<PromptAnswerResponse> toPrompts(List<PromptAnswer> answers) {
        return answers == null ? List.of() : answers.stream()
                .sorted(Comparator.comparingInt(PromptAnswer::getDisplayOrder))
                .map(this::toPrompt)
                .toList();
    }

    public List<TagResponse> toInterestTags(Set<Interest> interests) {
        return interests == null ? List.of() : interests.stream()
                .sorted(Comparator.comparing(Interest::getLabel))
                .map(i -> new TagResponse(i.getId(), i.getSlug(), i.getLabel(), i.getCategory(), i.getEmoji()))
                .toList();
    }

    public List<TagResponse> toQualityTags(Set<Quality> qualities) {
        return qualities == null ? List.of() : qualities.stream()
                .sorted(Comparator.comparing(Quality::getLabel))
                .map(q -> new TagResponse(q.getId(), q.getSlug(), q.getLabel(), q.getDimension(), null))
                .toList();
    }
}
