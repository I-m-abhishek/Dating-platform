package com.dating.platform.profile.controller;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.config.CacheConfig;
import com.dating.platform.profile.dto.TagResponse;
import com.dating.platform.profile.repository.InterestRepository;
import com.dating.platform.profile.repository.PromptRepository;
import com.dating.platform.profile.repository.QualityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Reference data the onboarding and edit screens need: interests, qualities, prompts.
 *
 * <p>Public and cached - it is the same for everybody and changes about once a quarter.
 */
@Tag(name = "Reference data", description = "Interests, qualities and prompts")
@RestController
@RequestMapping("/api/v1/reference")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final InterestRepository interestRepository;
    private final QualityRepository qualityRepository;
    private final PromptRepository promptRepository;

    @Operation(summary = "All selectable interests")
    @GetMapping("/interests")
    @Cacheable(cacheNames = CacheConfig.CACHE_REFERENCE_DATA, key = "'interests'")
    public ApiResponse<List<TagResponse>> interests() {
        return ApiResponse.success(interestRepository.findAllByActiveTrueOrderByCategoryAscLabelAsc().stream()
                .map(i -> new TagResponse(i.getId(), i.getSlug(), i.getLabel(), i.getCategory(), i.getEmoji()))
                .toList());
    }

    @Operation(summary = "All selectable qualities")
    @GetMapping("/qualities")
    @Cacheable(cacheNames = CacheConfig.CACHE_REFERENCE_DATA, key = "'qualities'")
    public ApiResponse<List<TagResponse>> qualities() {
        return ApiResponse.success(qualityRepository.findAllByActiveTrueOrderByDimensionAscLabelAsc().stream()
                .map(q -> new TagResponse(q.getId(), q.getSlug(), q.getLabel(), q.getDimension(), null))
                .toList());
    }

    @Operation(summary = "All available prompts")
    @GetMapping("/prompts")
    @Cacheable(cacheNames = CacheConfig.CACHE_REFERENCE_DATA, key = "'prompts'")
    public ApiResponse<List<PromptOption>> prompts() {
        return ApiResponse.success(promptRepository.findAllByActiveTrueOrderByDisplayOrderAscTextAsc().stream()
                .map(p -> new PromptOption(p.getId(), p.getSlug(), p.getText(), p.getCategory()))
                .toList());
    }

    public record PromptOption(UUID id, String slug, String text, String category) {
    }
}
