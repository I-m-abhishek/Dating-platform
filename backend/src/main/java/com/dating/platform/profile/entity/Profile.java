package com.dating.platform.profile.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Everything the user writes about themselves.
 *
 * <p>Interests and qualities are normalised into reference tables rather than free text:
 * the matching engine needs set overlap, and free text would make that meaningless.
 */
@Entity
@Table(name = "profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_profiles_user", columnNames = "user_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile extends BaseUuidEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_profiles_user"))
    private User user;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "company", length = 100)
    private String company;

    @Column(name = "school", length = 120)
    private String school;

    @Column(name = "education_level", length = 60)
    private String educationLevel;

    @Column(name = "hometown", length = 120)
    private String hometown;

    /** Centimetres - the UI converts for imperial locales. */
    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "religion", length = 60)
    private String religion;

    @Column(name = "politics", length = 60)
    private String politics;

    @Column(name = "zodiac_sign", length = 20)
    private String zodiacSign;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_intent", length = 40)
    private RelationshipIntent relationshipIntent;

    @Enumerated(EnumType.STRING)
    @Column(name = "drinking", length = 25)
    private LifestyleChoice drinking;

    @Enumerated(EnumType.STRING)
    @Column(name = "smoking", length = 25)
    private LifestyleChoice smoking;

    @Enumerated(EnumType.STRING)
    @Column(name = "cannabis", length = 25)
    private LifestyleChoice cannabis;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise", length = 25)
    private LifestyleChoice exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "children", length = 40)
    private ChildrenPreference children;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "profile_languages",
            joinColumns = @JoinColumn(name = "profile_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_profile_languages_profile")))
    @Column(name = "language", length = 40, nullable = false)
    @Builder.Default
    private Set<String> languages = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "profile_interests",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "interest_id"),
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_profile_interests_profile"),
            inverseForeignKey = @jakarta.persistence.ForeignKey(name = "fk_profile_interests_interest"))
    @Builder.Default
    private Set<Interest> interests = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "profile_qualities",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "quality_id"),
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_profile_qualities_profile"),
            inverseForeignKey = @jakarta.persistence.ForeignKey(name = "fk_profile_qualities_quality"))
    @Builder.Default
    private Set<Quality> qualities = new LinkedHashSet<>();

    /** Cached 0..1 completeness score; drives the "finish your profile" nudge and feed ranking. */
    @Column(name = "completeness", nullable = false)
    @Builder.Default
    private double completeness = 0d;
}
