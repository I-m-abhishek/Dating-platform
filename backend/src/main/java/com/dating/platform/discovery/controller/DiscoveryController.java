package com.dating.platform.discovery.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.discovery.dto.FeedCardResponse;
import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.discovery.service.DiscoveryService;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * The home feed.
 *
 * <p>POST rather than GET because the filter object is structured (sets of enums and ids)
 * and would not survive a query string legibly. It is idempotent and safe regardless.
 */
@Tag(name = "Discovery", description = "The home feed and its filters")
@Validated
@RestController
@RequestMapping("/api/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @Operation(summary = "Browse profiles",
            description = """
                    Returns ranked profiles that match the caller's preferences.
                    Distance and age always apply; height, intent, verification and interest
                    filters require a paid plan and return PREMIUM_REQUIRED otherwise.
                    """)
    @PostMapping("/feed")
    @RateLimit(name = "discovery.feed", capacity = 120, period = 1, unit = TimeUnit.MINUTES)
    public ApiResponse<PageResponse<FeedCardResponse>> feed(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody(required = false) FeedFilterRequest filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {

        FeedFilterRequest effective = filter == null ? FeedFilterRequest.empty() : filter;
        return ApiResponse.success(discoveryService.feed(principal.getId(), effective, page, size));
    }
}
