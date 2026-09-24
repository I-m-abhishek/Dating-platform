package com.dating.platform.interaction.repository;

import com.dating.platform.interaction.entity.Pass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PassRepository extends JpaRepository<Pass, UUID> {

    Optional<Pass> findBySenderIdAndReceiverId(UUID senderId, UUID receiverId);

    Optional<Pass> findFirstBySenderIdOrderByCreatedAtDesc(UUID senderId);

    void deleteBySenderIdAndReceiverId(UUID senderId, UUID receiverId);
}
