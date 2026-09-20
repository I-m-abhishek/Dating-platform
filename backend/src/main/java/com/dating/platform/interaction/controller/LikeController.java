package com.dating.platform.interaction.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.interaction.dto.LikeRequest;
import com.dating.platform.interaction.dto.LikeResultResponse;
import com.dating.platform.interaction.dto.LikesOverviewResponse;
import com.dating.platform.interaction.dto.PassRequest;
import com.dating.platform.interaction.service.LikeService;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "Likes", description = "Liking, passing, and the Likes You tab")
@Validated
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "Like someone",
            description = "Returns matched=true and the match when the like is reciprocated")
    @PostMapping
    @RateLimit(name = "like.send", capacity = 60, period = 1, unit = TimeUnit.MINUTES)
    public ApiResponse<LikeResultResponse> like(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody LikeRequest request) {
        return ApiResponse.success(likeService.like(principal.getId(), request));
    }

    @Operation(summary = "Pass on someone")
    @PostMapping("/pass")
    @RateLimit(name = "like.pass", capacity = 200, period = 1, unit = TimeUnit.MINUTES)
    public ApiResponse<Void> pass(@AuthenticationPrincipal UserPrincipal principal,
                                  @Valid @RequestBody PassRequest request) {
        likeService.pass(principal.getId(), request.targetUserId());
        return ApiResponse.success((Void) null);
    }

    @Operation(summary = "Undo the last pass", description = "Requires the REWIND feature")
    @PostMapping("/rewind")
    public ApiResponse<Map<String, UUID>> rewind(@AuthenticationPrincipal UserPrincipal principal) {
        UUID restored = likeService.rewindLastPass(principal.getId());
        return ApiResponse.success(Map.of("restoredUserId", restored), "Brought them back");
    }

    @Operation(summary = "People who liked me",
            description = """
                    Rows are redacted server side unless the caller has SEE_WHO_LIKES_YOU:
                    blurred rows carry no name, photo or note at all.
                    """)
    @GetMapping("/inbound")
    public ApiResponse<LikesOverviewResponse> inbound(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(likeService.inboundLikes(principal.getId(), PageRequest.of(page, size)));
    }

    @Operation(summary = "Mark the Likes tab as seen")
    @PostMapping("/inbound/seen")
    public ApiResponse<Void> markSeen(@AuthenticationPrincipal UserPrincipal principal) {
        likeService.markInboundSeen(principal.getId());
        return ApiResponse.success((Void) null);
    }

    @Operation(summary = "Unseen like count for the tab badge")
    @GetMapping("/inbound/unseen-count")
    public ApiResponse<Map<String, Long>> unseenCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(Map.of("count", likeService.unseenLikeCount(principal.getId())));
    }
}
