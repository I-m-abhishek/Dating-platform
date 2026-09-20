package com.dating.platform.user.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import com.dating.platform.user.entity.enums.Gender;
import com.dating.platform.user.entity.enums.Role;
import com.dating.platform.user.entity.enums.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Account aggregate: credentials, status, coarse location and discovery preferences.
 *
 * <p>Everything a user writes about themselves lives in {@link Profile}. The split keeps
 * the hot discovery query - which only needs age, gender and coordinates - narrow.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = {
                @Index(name = "idx_users_status", columnList = "status"),
                @Index(name = "idx_users_location", columnList = "latitude,longitude"),
                @Index(name = "idx_users_last_active", columnList = "last_active_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseUuidEntity {

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_ONBOARDING;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = Role.class)
    @CollectionTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_user_roles_user")))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Set<Role> roles = EnumSet.of(Role.USER);

    /** Genders this user wants to see. Empty set means everyone. */
    @ElementCollection(fetch = FetchType.EAGER, targetClass = Gender.class)
    @CollectionTable(name = "user_interested_in",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_user_interested_in_user")))
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    @Builder.Default
    private Set<Gender> interestedIn = EnumSet.noneOf(Gender.class);

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "preferred_min_age", nullable = false)
    @Builder.Default
    private Integer preferredMinAge = 18;

    @Column(name = "preferred_max_age", nullable = false)
    @Builder.Default
    private Integer preferredMaxAge = 45;

    @Column(name = "preferred_max_distance_km", nullable = false)
    @Builder.Default
    private Integer preferredMaxDistanceKm = 80;

    /** Ignore the distance filter entirely (a paid perk). */
    @Column(name = "global_mode", nullable = false)
    @Builder.Default
    private boolean globalMode = false;

    @Column(name = "photo_verified", nullable = false)
    @Builder.Default
    private boolean photoVerified = false;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "incognito", nullable = false)
    @Builder.Default
    private boolean incognito = false;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    public boolean isDiscoverable() {
        return status == UserStatus.ACTIVE && !incognito && latitude != null && longitude != null;
    }

    public boolean canAuthenticate() {
        return status != UserStatus.BANNED && status != UserStatus.DEACTIVATED;
    }
}
