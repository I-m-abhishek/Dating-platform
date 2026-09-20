package com.dating.platform.media.controller;

import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.media.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;

/**
 * Serves stored media.
 *
 * <p>Public by design: the URLs are unguessable UUID paths, which is the same trade-off a
 * CDN-backed bucket makes. If a deployment needs stricter control, put signed URLs in front
 * of {@link StorageService} - no caller changes.
 *
 * <p>Responses carry {@code Content-Disposition: inline} and a long cache lifetime because
 * the content at a given key never changes.
 */
@Tag(name = "Media", description = "Serving uploaded files")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final StorageService storageService;

    @Operation(summary = "Fetch a stored file")
    @GetMapping("/files/**")
    public ResponseEntity<Resource> file(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/api/v1/media/files/";
        if (fullPath == null || !fullPath.startsWith(prefix)) {
            throw new ResourceNotFoundException("File", "unknown");
        }
        String storageKey = fullPath.substring(prefix.length());

        Resource resource = storageService.load(storageKey);
        return ResponseEntity.ok()
                .contentType(contentTypeFor(storageKey))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    private MediaType contentTypeFor(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (lower.endsWith(".m4a")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        if (lower.endsWith(".weba")) {
            return MediaType.parseMediaType("audio/webm");
        }
        if (lower.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.IMAGE_JPEG;
    }
}
