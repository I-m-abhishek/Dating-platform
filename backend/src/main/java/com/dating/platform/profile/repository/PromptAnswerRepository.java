package com.dating.platform.profile.repository;

import com.dating.platform.profile.entity.PromptAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptAnswerRepository extends JpaRepository<PromptAnswer, UUID> {

    @EntityGraph(attributePaths = "prompt")
    List<PromptAnswer> findAllByUserIdOrderByDisplayOrderAsc(UUID userId);

    @EntityGraph(attributePaths = "prompt")
    List<PromptAnswer> findAllByUserIdInOrderByDisplayOrderAsc(java.util.Collection<UUID> userIds);

    Optional<PromptAnswer> findByIdAndUserId(UUID id, UUID userId);

    void deleteAllByUserId(UUID userId);

    long countByUserId(UUID userId);
}
