package com.dating.platform.safety.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_reported", columnList = "reported_user_id"),
        @Index(name = "idx_reports_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report extends BaseUuidEntity {

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ReportReason reason;

    @Column(name = "details", length = 1000)
    private String details;

    /** Optional pointer to the offending object (message, photo, comment). */
    @Column(name = "context_type", length = 30)
    private String contextType;

    @Column(name = "context_id")
    private UUID contextId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "moderator_note", length = 1000)
    private String moderatorNote;

    public enum ReportReason {
        FAKE_PROFILE,
        HARASSMENT,
        INAPPROPRIATE_CONTENT,
        SPAM_OR_SCAM,
        UNDERAGE,
        OFF_PLATFORM_BEHAVIOUR,
        OTHER
    }

    public enum ReportStatus {
        OPEN,
        REVIEWING,
        ACTIONED,
        DISMISSED
    }
}
