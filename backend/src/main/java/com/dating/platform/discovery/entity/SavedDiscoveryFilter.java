package com.dating.platform.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A user's saved filter sheet, stored as the JSON of a {@code FeedFilterRequest}. */
@Entity
@Table(name = "discovery_filters")
@Getter
@Setter
@NoArgsConstructor
public class SavedDiscoveryFilter {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "filters", nullable = false, length = 4000)
    private String filters;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SavedDiscoveryFilter(UUID userId) {
        this.userId = userId;
    }
}
