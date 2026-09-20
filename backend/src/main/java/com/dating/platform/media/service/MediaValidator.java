package com.dating.platform.media.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Content-type and size checks, run before anything touches storage.
 *
 * <p>The declared content type is only the first gate - a magic-byte check follows for
 * images, because {@code Content-Type} is client supplied and trivially forged.
 */
@Component
@RequiredArgsConstructor
public class MediaValidator {

    private final AppProperties appProperties;

    public void validateImage(MultipartFile file) {
        AppProperties.Storage storage = appProperties.storage();
        requireNonEmpty(file);
        requireType(file, storage.allowedImageTypes());
        requireSize(file, storage.maxImageBytes());
        requireImageMagicBytes(file);
    }

    public void validateAttachment(MultipartFile file) {
        AppProperties.Storage storage = appProperties.storage();
        requireNonEmpty(file);
        requireType(file, storage.allowedAttachmentTypes());
        requireSize(file, storage.maxAttachmentBytes());
    }

    private void requireNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No file was uploaded");
        }
    }

    private void requireType(MultipartFile file, java.util.List<String> allowed) {
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (allowed == null || !allowed.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA,
                    "That file type is not supported here");
        }
    }

    private void requireSize(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "That file is larger than " + (maxBytes / (1024 * 1024)) + " MB");
        }
    }

    /** Verifies the first bytes really are an image container, whatever the header claimed. */
    private void requireImageMagicBytes(MultipartFile file) {
        try {
            byte[] head = new byte[12];
            int read = file.getInputStream().readNBytes(head, 0, head.length);
            if (read < 4 || !looksLikeImage(head)) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA,
                        "That file does not look like an image");
            }
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Could not read the uploaded file");
        }
    }

    private boolean looksLikeImage(byte[] head) {
        boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
        boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
        boolean gif = head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
        boolean riffWebp = head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F';
        boolean heic = head.length >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
        return jpeg || png || gif || riffWebp || heic;
    }
}
