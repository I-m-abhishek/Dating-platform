package com.dating.platform.match.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.match.dto.MatchResponse;
import com.dating.platform.match.dto.UnmatchRequest;
import com.dating.platform.match.service.MatchService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Matches", description = "Your matches")
@Validated
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "List my matches",
            description = "Ordered by last interaction. Matches with no messages yet are flagged isNew")
    @GetMapping
    public ApiResponse<PageResponse<MatchResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(matchService.listMatches(principal.getId(), PageRequest.of(page, size)));
    }

    @Operation(summary = "Get one match")
    @GetMapping("/{matchId}")
    public ApiResponse<MatchResponse> get(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID matchId) {
        return ApiResponse.success(matchService.getMatch(principal.getId(), matchId));
    }

    @Operation(summary = "Match count for the tab badge")
    @GetMapping("/count")
    public ApiResponse<Map<String, Long>> count(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(Map.of("count", matchService.activeMatchCount(principal.getId())));
    }

    @Operation(summary = "Unmatch",
            description = "Closes the conversation and optionally blocks the other person")
    @DeleteMapping("/{matchId}")
    public ApiResponse<Void> unmatch(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID matchId,
                                     @Valid @RequestBody(required = false) UnmatchRequest request) {
        String reason = request == null ? null : request.reason();
        boolean alsoBlock = request != null && request.alsoBlock();
        matchService.unmatch(principal.getId(), matchId, reason, alsoBlock);
        return ApiResponse.success("Unmatched");
    }
}
