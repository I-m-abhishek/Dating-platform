package com.dating.platform.profile.entity;

import com.dating.platform.common.entity.BaseUuidEntity;
import com.dating.platform.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prompt_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_prompt_answers_user_prompt",
                columnNames = {"user_id", "prompt_id"}),
        indexes = @Index(name = "idx_prompt_answers_user", columnList = "user_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptAnswer extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prompt_answers_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prompt_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prompt_answers_prompt"))
    private Prompt prompt;

    @Column(name = "answer", nullable = false, length = 300)
    private String answer;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
