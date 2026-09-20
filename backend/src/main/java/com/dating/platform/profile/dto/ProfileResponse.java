package com.dating.platform.profile.dto;

import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The authenticated user's own profile, including fields others never see. */
@Schema(name = "ProfileResponse")
public record ProfileResponse(
        UUID userId,
        String displayName,
        int age,
        Gender gender,
        Set<Gender> interestedIn,
        String city,
        String country,
        String bio,
        String jobTitle,
        String company,
        String school,
        String educationLevel,
        String hometown,
        Integer heightCm,
        String religion,
        String politics,
        String zodiacSign,
        RelationshipIntent relationshipIntent,
        LifestyleChoice drinking,
        LifestyleChoice smoking,
        LifestyleChoice cannabis,
        LifestyleChoice exercise,
        ChildrenPreference children,
        Set<String> languages,
        List<TagResponse> interests,
        List<TagResponse> qualities,
        List<PhotoResponse> photos,
        List<PromptAnswerResponse> prompts,
        double completeness,
        boolean photoVerified,
        boolean incognito
) {
}
