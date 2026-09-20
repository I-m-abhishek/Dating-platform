package com.dating.platform.subscription.repository;

import com.dating.platform.subscription.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findAllByActiveTrueOrderByDisplayOrderAsc();

    Optional<Plan> findByCode(String code);
}
