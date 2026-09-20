package com.dating.platform.profile.dto;

import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What another user is allowed to see.
 *
 * <p>Note what is absent: email, exact coordinates, last active timestamp precision,
 * and whether this person has already liked you (that is the Likes tab's job, and it is
 * deliberately blurred for free accounts).
 */
@Schema(name = "PublicProfileResponse")
public record PublicProfileResponse(
        UUID userId,
        String displayName,
        int age,
        Gender gender,
        String city,
        Integer distanceKm,
        String bio,
        String jobTitle,
        String company,
        String school,
        String hometown,
        Integer heightCm,
        String religion,
        String zodiacSign,
        RelationshipIntent relationshipIntent,
        LifestyleChoice drinking,
        LifestyleChoice smoking,
        ChildrenPreference children,
        Set<String> languages,
        List<TagResponse> interests,
        List<TagResponse> qualities,
        List<PhotoResponse> photos,
        List<PromptAnswerResponse> prompts,
        boolean photoVerified,
        boolean recentlyActive,
        Instant lastActiveAt,
        Double compatibilityScore,
        List<String> sharedInterests,

        /**
         * Where the viewer already stands with this person. Without it the profile screen
         * cannot tell whether to offer "Like" or "Message", and would happily let someone
         * like a person they matched with last week.
         */
        Relationship relationship
) {

    @Schema(name = "Relationship")
    public record Relationship(
            boolean matched,
            UUID matchId,
            UUID conversationId,
            Instant matchedAt,
            /** The viewer has sent a like that has not been answered yet. */
            boolean likeSent
    ) {

        public static Relationship none() {
            return new Relationship(false, null, null, null, false);
        }

        /** Named pendingLike, not likeSent - a record's accessor already owns that name. */
        public static Relationship pendingLike() {
            return new Relationship(false, null, null, null, true);
        }
    }
}
