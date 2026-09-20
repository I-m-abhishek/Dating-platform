package com.dating.platform.media.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Object storage abstraction.
 *
 * <p>The local implementation ships with the project so a developer needs nothing but a
 * database to run it. Swapping in S3, GCS or Azure Blob means one new implementation of
 * this interface - no service above it changes, because nothing above it knows what a
 * storage key is beyond an opaque string.
 */
public interface StorageService {

    /**
     * @param folder logical prefix, e.g. {@code photos/<userId>}
     * @return the stored object, including the URL clients should use
     */
    StoredFile store(MultipartFile file, String folder);

    void delete(String storageKey);

    Resource load(String storageKey);

    boolean exists(String storageKey);

    /** Result of a successful upload. */
    record StoredFile(
            String storageKey,
            String url,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {
    }
}
