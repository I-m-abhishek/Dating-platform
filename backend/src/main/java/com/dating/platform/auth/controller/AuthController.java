package com.dating.platform.auth.controller;

import com.dating.platform.auth.dto.AuthResponse;
import com.dating.platform.auth.dto.ChangePasswordRequest;
import com.dating.platform.auth.dto.LoginRequest;
import com.dating.platform.auth.dto.RefreshRequest;
import com.dating.platform.auth.dto.RegisterRequest;
import com.dating.platform.auth.service.AuthService;
import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Authentication endpoints.
 *
 * <p>All of these are public, so they carry the tightest rate limits in the application and
 * are scoped by IP rather than by user - there is no user yet to scope by.
 */
@Tag(name = "Authentication", description = "Register, sign in, refresh and sign out")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Create an account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "auth.register", capacity = 5, period = 1, unit = TimeUnit.HOURS,
            scope = RateLimit.Scope.IP)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                              HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request,
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return ApiResponse.success(response, "Welcome aboard");
    }

    @Operation(summary = "Sign in")
    @PostMapping("/login")
    @RateLimit(name = "auth.login", capacity = 10, period = 5, unit = TimeUnit.MINUTES,
            scope = RateLimit.Scope.IP)
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.login(request,
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @PostMapping("/refresh")
    @RateLimit(name = "auth.refresh", capacity = 30, period = 5, unit = TimeUnit.MINUTES,
            scope = RateLimit.Scope.IP)
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.refresh(request.refreshToken(),
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @Operation(summary = "Sign out of this device")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ApiResponse.success("Signed out");
    }

    @Operation(summary = "Sign out everywhere", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutEverywhere(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logoutEverywhere(principal.getId());
        return ApiResponse.success("Signed out of all devices");
    }

    @Operation(summary = "Change password", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/change-password")
    @RateLimit(name = "auth.change-password", capacity = 5, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ApiResponse.success("Password updated - sign in again on your other devices");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
