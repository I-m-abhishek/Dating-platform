package com.dating.platform.discovery.repository;

import com.dating.platform.discovery.entity.SavedDiscoveryFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SavedDiscoveryFilterRepository extends JpaRepository<SavedDiscoveryFilter, UUID> {
}
