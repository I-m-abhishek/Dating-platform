package com.dating.platform.profile.repository;

import com.dating.platform.profile.entity.Quality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface QualityRepository extends JpaRepository<Quality, UUID> {

    List<Quality> findAllByActiveTrueOrderByDimensionAscLabelAsc();

    List<Quality> findAllByIdIn(Set<UUID> ids);
}
