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

/** A question a user can answer on their profile ("A life goal of mine..."). */
@Entity
@Table(name = "prompts",
        uniqueConstraints = @UniqueConstraint(name = "uk_prompts_slug", columnNames = "slug"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prompt extends BaseUuidEntity {

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "text", nullable = false, length = 160)
    private String text;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
