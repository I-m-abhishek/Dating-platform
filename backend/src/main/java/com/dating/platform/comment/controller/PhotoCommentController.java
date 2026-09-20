package com.dating.platform.comment.controller;

import com.dating.platform.comment.dto.CommentQuotaResponse;
import com.dating.platform.comment.dto.CommentResponse;
import com.dating.platform.comment.dto.CreateCommentRequest;
import com.dating.platform.comment.service.PhotoCommentService;
import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Comments on profile photos.
 *
 * <p>The free allowance is five a day. When it is spent the API answers
 * {@code 402 QUOTA_EXCEEDED} with the reset time and an upgrade hint, which is exactly what
 * the paywall sheet renders.
 */
@Tag(name = "Photo comments", description = "Comment on other people's photos")
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PhotoCommentController {

    private final PhotoCommentService photoCommentService;

    @Operation(summary = "Comment on a photo",
            description = "Consumes one of the caller's daily comment allowance")
    @PostMapping("/photos/{photoId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "comment.create", capacity = 30, period = 1, unit = TimeUnit.MINUTES)
    public ApiResponse<CommentResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable UUID photoId,
                                               @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(photoCommentService.create(principal.getId(), photoId, request));
    }

    @Operation(summary = "List comments on a photo")
    @GetMapping("/photos/{photoId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID photoId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(
                photoCommentService.listForPhoto(principal.getId(), photoId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Comments left on my photos")
    @GetMapping("/profile/photo-comments")
    public ApiResponse<PageResponse<CommentResponse>> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(
                photoCommentService.listForMyPhotos(principal.getId(), PageRequest.of(page, size)));
    }

    @Operation(summary = "How many comments I have left today")
    @GetMapping("/comments/quota")
    public ApiResponse<CommentQuotaResponse> quota(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(photoCommentService.quotaFor(principal.getId()));
    }

    @Operation(summary = "Remove a comment",
            description = "Allowed for the comment author and for the owner of the photo")
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID commentId) {
        photoCommentService.delete(principal.getId(), commentId);
        return ApiResponse.success("Comment removed");
    }
}
