package com.dating.platform.profile.repository;

import com.dating.platform.profile.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID> {

    List<Prompt> findAllByActiveTrueOrderByDisplayOrderAscTextAsc();
}
