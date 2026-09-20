package com.dating.platform.standout.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.security.UserPrincipal;
import com.dating.platform.standout.dto.StandoutResponse;
import com.dating.platform.standout.service.StandoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Standouts", description = "A curated shelf of people worth seeing")
@Validated
@RestController
@RequestMapping("/api/v1/standouts")
@RequiredArgsConstructor
public class StandoutController {

    private final StandoutService standoutService;

    @Operation(summary = "Today's standouts",
            description = """
                    Ranked by a blend of recent popularity (log-scaled), profile completeness,
                    recent activity and a newcomer boost, then filtered to the caller's
                    preferences and excluding anyone they have already acted on.
                    """)
    @GetMapping
    public ApiResponse<List<StandoutResponse>> standouts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return ApiResponse.success(standoutService.standoutsFor(principal.getId(), limit));
    }
}
