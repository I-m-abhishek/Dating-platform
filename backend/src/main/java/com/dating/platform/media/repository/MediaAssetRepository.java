package com.dating.platform.media.repository;

import com.dating.platform.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Optional<MediaAsset> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<MediaAsset> findAllByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);

    /** Orphans: uploaded but never attached. Swept by a scheduled job. */
    List<MediaAsset> findAllByConsumedFalseAndCreatedAtBefore(Instant cutoff);
}
