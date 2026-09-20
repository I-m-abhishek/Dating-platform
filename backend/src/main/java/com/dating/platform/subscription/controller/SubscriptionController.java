package com.dating.platform.subscription.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.security.UserPrincipal;
import com.dating.platform.subscription.dto.Entitlements;
import com.dating.platform.subscription.dto.PlanResponse;
import com.dating.platform.subscription.dto.SubscribeRequest;
import com.dating.platform.subscription.dto.SubscriptionResponse;
import com.dating.platform.subscription.service.SubscriptionService;
import com.dating.platform.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "Plans and subscriptions", description = "Paid plans and what they unlock")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "List available plans", description = "Public - used by the paywall screens")
    @GetMapping("/plans")
    public ApiResponse<List<PlanResponse>> plans() {
        return ApiResponse.success(subscriptionService.listPlans());
    }

    @Operation(summary = "What I am entitled to",
            description = """
                    The single source of truth for every limit and unlock in the client.
                    Limits use -1 for unlimited. Never hardcode these numbers in the UI.
                    """)
    @GetMapping("/subscriptions/me/entitlements")
    public ApiResponse<Entitlements> entitlements(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(subscriptionService.entitlements(principal.getId()));
    }

    @Operation(summary = "My current subscription")
    @GetMapping("/subscriptions/me")
    public ApiResponse<SubscriptionResponse> current(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(subscriptionService.current(principal.getId()).orElse(null));
    }

    @Operation(summary = "Subscribe to a plan",
            description = "Payment verification is stubbed in this build; the plan is granted immediately")
    @PostMapping("/subscriptions")
    @RateLimit(name = "subscription.create", capacity = 10, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<SubscriptionResponse> subscribe(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody SubscribeRequest request) {
        return ApiResponse.success(subscriptionService.subscribe(principal.getId(), request),
                "Your plan is active");
    }

    @Operation(summary = "Cancel auto-renewal",
            description = "Access continues until the end of the current paid period")
    @DeleteMapping("/subscriptions/me")
    public ApiResponse<SubscriptionResponse> cancel(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(subscriptionService.cancel(principal.getId()),
                "Auto-renewal is off");
    }
}
