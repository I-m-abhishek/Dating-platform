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

/**
 * A character trait the user claims ("Ambitious", "Empathetic").
 *
 * <p>Qualities carry a {@code complementWeight}: some traits match best with the same
 * trait (shared values) and some with their opposite. The matching engine uses this,
 * which is why qualities are reference data and not free text.
 */
@Entity
@Table(name = "qualities",
        uniqueConstraints = @UniqueConstraint(name = "uk_qualities_slug", columnNames = "slug"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quality extends BaseUuidEntity {

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "dimension", nullable = false, length = 40)
    private String dimension;

    /** 1.0 = pairs best with an identical trait, -1.0 = pairs best with its opposite. */
    @Column(name = "affinity_weight", nullable = false)
    @Builder.Default
    private double affinityWeight = 1.0d;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
