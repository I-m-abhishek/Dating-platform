package com.dating.platform.profile.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.media.service.MediaValidator;
import com.dating.platform.media.service.StorageService;
import com.dating.platform.profile.dto.PhotoResponse;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.mapper.ProfileMapper;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Profile photo management: upload, reorder, set primary, delete.
 *
 * <p>Ordering is explicit rather than implicit in insertion time, because the first photo
 * is the one the whole product is judged on and users reorder constantly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MediaValidator mediaValidator;
    private final ProfileMapper profileMapper;
    private final ProfileService profileService;

    @Transactional
    public PhotoResponse upload(UUID userId, MultipartFile file, String caption) {
        mediaValidator.validateImage(file);

        long existing = photoRepository.countByUserId(userId);
        if (existing >= Photo.MAX_PHOTOS_PER_USER) {
            throw new BusinessException(ErrorCode.PHOTO_LIMIT_REACHED,
                    "You can have at most " + Photo.MAX_PHOTOS_PER_USER + " photos");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        StorageService.StoredFile stored = storageService.store(file, "photos/" + userId);

        Photo photo = photoRepository.save(Photo.builder()
                .user(user)
                .storageKey(stored.storageKey())
                .url(stored.url())
                .width(stored.width())
                .height(stored.height())
                .caption(caption)
                .displayOrder((int) existing)
                .primaryPhoto(existing == 0)
                .build());

        profileService.recalculateCompleteness(userId);
        log.debug("User {} uploaded photo {}", userId, photo.getId());
        return profileMapper.toPhoto(photo);
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listOwn(UUID userId) {
        return profileMapper.toPhotos(photoRepository.findAllByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID photoId) {
        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));

        boolean wasPrimary = photo.isPrimaryPhoto();
        String storageKey = photo.getStorageKey();
        photoRepository.delete(photo);
        photoRepository.flush();

        List<Photo> remaining = photoRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i);
        }
        if (wasPrimary && !remaining.isEmpty()) {
            remaining.get(0).setPrimaryPhoto(true);
        }
        photoRepository.saveAll(remaining);

        // Storage cleanup last: a stale blob is recoverable, a row pointing at nothing is not.
        storageService.delete(storageKey);
        profileService.recalculateCompleteness(userId);
    }

    @Transactional
    public List<PhotoResponse> reorder(UUID userId, List<UUID> photoIdsInOrder) {
        List<Photo> photos = photoRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
        if (photos.size() != photoIdsInOrder.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Send every photo id exactly once, in the new order");
        }
        for (Photo photo : photos) {
            int index = photoIdsInOrder.indexOf(photo.getId());
            if (index < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown photo in the new order");
            }
            photo.setDisplayOrder(index);
            photo.setPrimaryPhoto(index == 0);
        }
        photoRepository.saveAll(photos);
        return profileMapper.toPhotos(photoRepository.findAllByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Transactional
    public List<PhotoResponse> setPrimary(UUID userId, UUID photoId) {
        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        photoRepository.clearPrimary(userId);
        photo.setPrimaryPhoto(true);
        photoRepository.save(photo);
        return listOwn(userId);
    }

    @Transactional
    public PhotoResponse updateCaption(UUID userId, UUID photoId, String caption) {
        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        photo.setCaption(caption == null || caption.isBlank() ? null : caption.trim());
        return profileMapper.toPhoto(photoRepository.save(photo));
    }
}
