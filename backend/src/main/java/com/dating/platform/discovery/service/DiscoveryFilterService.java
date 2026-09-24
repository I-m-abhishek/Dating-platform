package com.dating.platform.discovery.service;

import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.discovery.entity.SavedDiscoveryFilter;
import com.dating.platform.discovery.repository.SavedDiscoveryFilterRepository;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.service.EntitlementService;
import com.dating.platform.user.dto.UpdatePreferencesRequest;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The filter sheet, saved.
 *
 * <p>Age, distance and "show me" are written through to the user's preferences - the same
 * values Settings, the profile and auto-match use - so there is one answer to "who am I
 * looking for", not one per screen. The rest is stored as a JSON document.
 *
 * <p>Paid filters are only returned to accounts that can use them. A lapsed subscription
 * keeps its saved filters (they come back on renewal) but the feed stops applying them,
 * instead of failing every request with PREMIUM_REQUIRED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryFilterService {

    private final SavedDiscoveryFilterRepository repository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FeedFilterRequest get(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        FeedFilterRequest saved = repository.findById(userId)
                .map(this::read)
                .orElseGet(FeedFilterRequest::empty);

        Set<Gender> showMe = user.getInterestedIn() == null || user.getInterestedIn().isEmpty()
                ? null : EnumSet.copyOf(user.getInterestedIn());
        FeedFilterRequest merged = saved.withPreferences(
                user.getPreferredMinAge(), user.getPreferredMaxAge(), user.getPreferredMaxDistanceKm(), showMe);

        return entitlementService.has(userId, Feature.ADVANCED_FILTERS) ? merged : merged.withoutAdvanced();
    }

    @Transactional
    public FeedFilterRequest save(UUID userId, FeedFilterRequest filter) {
        if (filter.usesAdvancedFilters()) {
            entitlementService.require(userId, Feature.ADVANCED_FILTERS);
        }

        userService.updatePreferences(userId, new UpdatePreferencesRequest(
                filter.minAge(), filter.maxAge(), filter.maxDistanceKm(), filter.genders(), null, null));

        SavedDiscoveryFilter row = repository.findById(userId).orElseGet(() -> new SavedDiscoveryFilter(userId));
        row.setFilters(write(filter.withPreferences(null, null, null, null)));
        row.setUpdatedAt(Instant.now());
        repository.save(row);

        return get(userId);
    }

    private FeedFilterRequest read(SavedDiscoveryFilter row) {
        try {
            return objectMapper.readValue(row.getFilters(), FeedFilterRequest.class);
        } catch (JsonProcessingException e) {
            // A document from an older shape must not lock someone out of their feed.
            log.warn("Unreadable saved filters for {} - ignoring them", row.getUserId(), e);
            return FeedFilterRequest.empty();
        }
    }

    private String write(FeedFilterRequest filter) {
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise filters", e);
        }
    }
}
