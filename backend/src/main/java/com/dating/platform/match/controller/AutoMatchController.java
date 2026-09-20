package com.dating.platform.match.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.match.dto.AutoMatchResponse;
import com.dating.platform.match.dto.AutoMatchRunResponse;
import com.dating.platform.match.service.AutoMatchService;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Auto-match: the guarantee that nobody stays unmatched.
 *
 * <p>Free accounts get one match a week, Premium gets one a day. The scheduler runs it
 * automatically; these endpoints let the user trigger their own run early and inspect the
 * outcome.
 */
@Tag(name = "Auto-match", description = "Weekly and daily automatic matching")
@RestController
@RequestMapping("/api/v1/auto-match")
@RequiredArgsConstructor
public class AutoMatchController {

    private final AutoMatchService autoMatchService;

    @Operation(summary = "Run my auto-match now",
            description = """
                    Uses the caller's entitled cadence (weekly on free, daily on Premium).
                    Returns QUOTA_EXCEEDED when the allowance for the period is already spent.
                    An outcome of NO_CANDIDATE or BELOW_THRESHOLD is a successful response,
                    not an error - we would rather return nothing than a bad match.
                    """)
    @PostMapping("/run")
    @RateLimit(name = "automatch.run", capacity = 10, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<AutoMatchResponse> run(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(autoMatchService.runOnDemand(principal.getId()));
    }

    @Operation(summary = "My recent auto-match runs")
    @GetMapping("/history")
    public ApiResponse<List<AutoMatchRunResponse>> history(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(autoMatchService.history(principal.getId()));
    }
}
