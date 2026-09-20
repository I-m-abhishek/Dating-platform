package com.dating.platform.profile.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.profile.dto.PhotoResponse;
import com.dating.platform.profile.dto.ReorderPhotosRequest;
import com.dating.platform.profile.service.PhotoService;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "Photos", description = "Profile photos")
@Validated
@RestController
@RequestMapping("/api/v1/profile/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @Operation(summary = "List my photos")
    @GetMapping
    public ApiResponse<List<PhotoResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(photoService.listOwn(principal.getId()));
    }

    @Operation(summary = "Upload a photo")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "photo.upload", capacity = 30, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<PhotoResponse> upload(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) @Size(max = 140) String caption) {
        return ApiResponse.success(photoService.upload(principal.getId(), file, caption), "Photo added");
    }

    @Operation(summary = "Reorder photos - the first one becomes the primary")
    @PutMapping("/order")
    public ApiResponse<List<PhotoResponse>> reorder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody ReorderPhotosRequest request) {
        return ApiResponse.success(photoService.reorder(principal.getId(), request.photoIdsInOrder()));
    }

    @Operation(summary = "Make a photo the primary one")
    @PutMapping("/{photoId}/primary")
    public ApiResponse<List<PhotoResponse>> setPrimary(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID photoId) {
        return ApiResponse.success(photoService.setPrimary(principal.getId(), photoId));
    }

    @Operation(summary = "Update a photo caption")
    @PatchMapping("/{photoId}/caption")
    public ApiResponse<PhotoResponse> updateCaption(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID photoId,
                                                    @RequestParam(required = false) @Size(max = 140) String caption) {
        return ApiResponse.success(photoService.updateCaption(principal.getId(), photoId, caption));
    }

    @Operation(summary = "Delete a photo")
    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID photoId) {
        photoService.delete(principal.getId(), photoId);
        return ApiResponse.success("Photo removed");
    }
}
