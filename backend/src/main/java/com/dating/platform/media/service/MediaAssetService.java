package com.dating.platform.media.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.media.dto.MediaAssetResponse;
import com.dating.platform.media.entity.MediaAsset;
import com.dating.platform.media.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Two-step upload: store the blob, hand back an id, then let another call consume it.
 *
 * <p>Consumption is one-shot and ownership-checked, which is what stops one user attaching
 * another user's upload to their own message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAssetService {

    private static final Duration ORPHAN_TTL = Duration.ofHours(24);

    private final MediaAssetRepository assetRepository;
    private final StorageService storageService;
    private final MediaValidator mediaValidator;

    @Transactional
    public MediaAssetResponse uploadChatAttachment(UUID ownerId, MultipartFile file,
                                                   Integer durationSeconds, String waveform) {
        mediaValidator.validateAttachment(file);
        StorageService.StoredFile stored = storageService.store(file, "chat/" + ownerId);

        MediaAsset asset = assetRepository.save(MediaAsset.builder()
                .ownerId(ownerId)
                .storageKey(stored.storageKey())
                .url(stored.url())
                .contentType(stored.contentType())
                .fileName(sanitiseName(file.getOriginalFilename()))
                .sizeBytes(stored.sizeBytes())
                .width(stored.width())
                .height(stored.height())
                .durationSeconds(durationSeconds)
                .waveform(waveform)
                .purpose(MediaAsset.Purpose.CHAT_ATTACHMENT)
                .build());

        return MediaAssetResponse.from(asset);
    }

    /**
     * Claims the assets for use. Verifies ownership, rejects already-used ids, and marks
     * them consumed in the caller's transaction so a failed send releases nothing.
     */
    @Transactional
    public List<MediaAsset> consume(UUID ownerId, Collection<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        List<MediaAsset> assets = assetRepository.findAllByIdInAndOwnerId(assetIds, ownerId);
        if (assets.size() != assetIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "One or more attachments are not available");
        }
        for (MediaAsset asset : assets) {
            if (asset.isConsumed()) {
                throw new BusinessException(ErrorCode.CONFLICT, "That attachment has already been sent");
            }
            asset.setConsumed(true);
        }
        assetRepository.saveAll(assets);
        return assets;
    }

    /** Hourly sweep of uploads that were never attached to anything. */
    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    @Transactional
    public void purgeOrphans() {
        List<MediaAsset> orphans =
                assetRepository.findAllByConsumedFalseAndCreatedAtBefore(Instant.now().minus(ORPHAN_TTL));
        if (orphans.isEmpty()) {
            return;
        }
        orphans.forEach(asset -> storageService.delete(asset.getStorageKey()));
        assetRepository.deleteAll(orphans);
        log.info("Purged {} orphaned media assets", orphans.size());
    }

    private String sanitiseName(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        String cleaned = original.replaceAll("[\\\\/\\r\\n\\t]", "_").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
