package com.dating.platform.call.controller;

import com.dating.platform.call.dto.CallResponse;
import com.dating.platform.call.dto.StartCallRequest;
import com.dating.platform.call.service.CallService;
import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Call control. Only the lifecycle is REST; the SDP/ICE exchange goes over STOMP and the
 * media itself never touches this server.
 */
@Tag(name = "Calls", description = "Voice and video calls between matches")
@Validated
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @Operation(summary = "Start a call in a conversation",
            description = "Returns the ICE servers the browser needs for the peer connection")
    @PostMapping("/conversations/{conversationId}")
    @RateLimit(name = "call.start", capacity = 20, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<CallResponse> start(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID conversationId,
                                           @RequestBody(required = false) StartCallRequest request) {
        StartCallRequest effective = request == null ? new StartCallRequest(null) : request;
        return ApiResponse.success(
                callService.start(principal.getId(), conversationId, effective.typeOrDefault()));
    }

    @Operation(summary = "Accept an incoming call")
    @PostMapping("/{callId}/accept")
    public ApiResponse<CallResponse> accept(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID callId) {
        return ApiResponse.success(callService.accept(principal.getId(), callId));
    }

    @Operation(summary = "Decline an incoming call")
    @PostMapping("/{callId}/decline")
    public ApiResponse<CallResponse> decline(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID callId) {
        return ApiResponse.success(callService.decline(principal.getId(), callId));
    }

    @Operation(summary = "Hang up")
    @PostMapping("/{callId}/hangup")
    public ApiResponse<CallResponse> hangUp(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID callId) {
        return ApiResponse.success(callService.hangUp(principal.getId(), callId));
    }

    @Operation(summary = "Call history for a conversation")
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<List<CallResponse>> history(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ApiResponse.success(
                callService.history(principal.getId(), conversationId, PageRequest.of(0, limit)));
    }
}
