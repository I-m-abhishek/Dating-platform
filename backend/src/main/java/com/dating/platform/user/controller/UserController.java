package com.dating.platform.user.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.security.UserPrincipal;
import com.dating.platform.user.dto.AccountResponse;
import com.dating.platform.user.dto.UpdateLocationRequest;
import com.dating.platform.user.dto.UpdatePreferencesRequest;
import com.dating.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "The signed-in user's own account")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get my account")
    @GetMapping
    public ApiResponse<AccountResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(userService.getAccount(principal.getId()));
    }

    @Operation(summary = "Update discovery preferences")
    @PatchMapping("/preferences")
    public ApiResponse<AccountResponse> updatePreferences(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.success(userService.updatePreferences(principal.getId(), request),
                "Preferences saved");
    }

    @Operation(summary = "Update my location")
    @PutMapping("/location")
    public ApiResponse<AccountResponse> updateLocation(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody UpdateLocationRequest request) {
        return ApiResponse.success(userService.updateLocation(principal.getId(), request));
    }

    @Operation(summary = "Finish onboarding and become discoverable")
    @PostMapping("/complete-onboarding")
    public ApiResponse<AccountResponse> completeOnboarding(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(userService.completeOnboarding(principal.getId()),
                "You are all set");
    }

    @Operation(summary = "Hide my profile without deleting it")
    @PostMapping("/pause")
    public ApiResponse<Void> pause(@AuthenticationPrincipal UserPrincipal principal) {
        userService.pause(principal.getId());
        return ApiResponse.success("Your profile is hidden");
    }

    @Operation(summary = "Deactivate my account")
    @DeleteMapping
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal UserPrincipal principal) {
        userService.deactivate(principal.getId());
        return ApiResponse.success("Your account has been deactivated");
    }

    @Operation(summary = "Heartbeat - marks me as recently active")
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(@AuthenticationPrincipal UserPrincipal principal) {
        userService.touchActivity(principal.getId());
        return ApiResponse.success((Void) null);
    }
}
