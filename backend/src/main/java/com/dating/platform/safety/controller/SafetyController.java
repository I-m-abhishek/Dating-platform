package com.dating.platform.safety.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.safety.dto.BlockRequest;
import com.dating.platform.safety.dto.CreateReportRequest;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.safety.service.ReportService;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "Safety", description = "Blocking and reporting")
@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final BlockService blockService;
    private final ReportService reportService;

    @Operation(summary = "Block someone",
            description = "Blocks are symmetric in effect: neither of you will see the other anywhere")
    @PostMapping("/blocks")
    @RateLimit(name = "safety.block", capacity = 60, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<Void> block(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody BlockRequest request) {
        blockService.block(principal.getId(), request.userId(), request.reason());
        return ApiResponse.success("Blocked");
    }

    @Operation(summary = "Unblock someone")
    @DeleteMapping("/blocks/{userId}")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID userId) {
        blockService.unblock(principal.getId(), userId);
        return ApiResponse.success("Unblocked");
    }

    @Operation(summary = "Everyone hidden from me by a block, in either direction")
    @GetMapping("/blocks")
    public ApiResponse<List<UUID>> blocked(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(blockService.hiddenUserIdsFor(principal.getId()));
    }

    @Operation(summary = "Report someone")
    @PostMapping("/reports")
    @RateLimit(name = "safety.report", capacity = 20, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<Map<String, UUID>> report(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody CreateReportRequest request) {
        UUID reportId = reportService.report(principal.getId(), request);
        return ApiResponse.success(
                reportId == null ? Map.<String, UUID>of() : Map.of("reportId", reportId),
                "Thanks - our team will take a look");
    }
}
