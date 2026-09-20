package com.dating.platform.profile.repository;

import com.dating.platform.profile.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface InterestRepository extends JpaRepository<Interest, UUID> {

    List<Interest> findAllByActiveTrueOrderByCategoryAscLabelAsc();

    List<Interest> findAllByIdIn(Set<UUID> ids);
}
