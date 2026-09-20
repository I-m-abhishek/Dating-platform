package com.dating.platform.profile.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.profile.dto.ProfileResponse;
import com.dating.platform.profile.dto.PromptAnswerResponse;
import com.dating.platform.profile.dto.PublicProfileResponse;
import com.dating.platform.profile.dto.UpdateProfileRequest;
import com.dating.platform.profile.dto.UpsertPromptAnswerRequest;
import com.dating.platform.profile.service.ProfileService;
import com.dating.platform.profile.service.PromptAnswerService;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "Profile", description = "Profile content: about you, prompts, and other people's profiles")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PromptAnswerService promptAnswerService;

    @Operation(summary = "Get my profile")
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> myProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(profileService.getOwnProfile(principal.getId()));
    }

    @Operation(summary = "Update my profile",
            description = "Partial update - omit a field to leave it unchanged, send an empty value to clear it")
    @PatchMapping("/profile")
    @RateLimit(name = "profile.update", capacity = 60, period = 1, unit = TimeUnit.MINUTES)
    public ApiResponse<ProfileResponse> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(profileService.updateProfile(principal.getId(), request), "Profile saved");
    }

    @Operation(summary = "View someone else's profile")
    @GetMapping("/profiles/{userId}")
    public ApiResponse<PublicProfileResponse> publicProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID userId) {
        return ApiResponse.success(profileService.getPublicProfile(principal.getId(), userId));
    }

    @Operation(summary = "List my prompt answers")
    @GetMapping("/profile/prompts")
    public ApiResponse<List<PromptAnswerResponse>> myPrompts(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(promptAnswerService.listOwn(principal.getId()));
    }

    @Operation(summary = "Add or replace a prompt answer")
    @PutMapping("/profile/prompts")
    public ApiResponse<List<PromptAnswerResponse>> upsertPrompt(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertPromptAnswerRequest request) {
        return ApiResponse.success(promptAnswerService.upsert(principal.getId(), request), "Prompt saved");
    }

    @Operation(summary = "Remove a prompt answer")
    @DeleteMapping("/profile/prompts/{answerId}")
    public ApiResponse<List<PromptAnswerResponse>> deletePrompt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID answerId) {
        return ApiResponse.success(promptAnswerService.delete(principal.getId(), answerId));
    }
}
