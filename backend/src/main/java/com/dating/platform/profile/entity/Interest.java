package com.dating.platform.profile.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reference data: "Hiking", "Live music", "Board games". Seeded by migration V2. */
@Entity
@Table(name = "interests",
        uniqueConstraints = @UniqueConstraint(name = "uk_interests_slug", columnNames = "slug"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interest extends BaseUuidEntity {

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "emoji", length = 8)
    private String emoji;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
