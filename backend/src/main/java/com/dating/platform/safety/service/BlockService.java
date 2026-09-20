package com.dating.platform.safety.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.safety.entity.Block;
import com.dating.platform.safety.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Blocking is the foundation of every visibility rule on the platform.
 *
 * <p>Any query that can surface another human - discovery, likes, standouts, comments,
 * chat - asks this service first. Blocks are enforced symmetrically even though they are
 * stored one-directionally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return blockRepository.existsBetween(a, b);
    }

    /** Ids to exclude from any listing shown to this user. */
    @Transactional(readOnly = true)
    public List<UUID> hiddenUserIdsFor(UUID userId) {
        return blockRepository.findAllCounterpartIds(userId);
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId, String reason) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ErrorCode.SELF_INTERACTION, "You cannot block yourself");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return; // idempotent
        }
        blockRepository.save(Block.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .reason(reason)
                .build());
        log.info("User {} blocked user {}", blockerId, blockedId);
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /** Guard for write paths: throws instead of returning a boolean. */
    @Transactional(readOnly = true)
    public void assertNotBlocked(UUID a, UUID b) {
        if (isBlockedEitherWay(a, b)) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
    }
}
