package com.dating.platform.media.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Filesystem-backed {@link StorageService} for local development and single-node installs.
 *
 * <p>Two things here are security relevant rather than incidental:
 * <ul>
 *   <li>Generated file names. The client's file name is never used as a path component -
 *       it is replaced by a UUID plus a whitelisted extension, which removes path traversal
 *       and content-sniffing tricks in one move.</li>
 *   <li>Root containment. Every resolved path is checked to be inside the configured root
 *       before any read or delete.</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;
    private final String publicBaseUrl;
    private final ImageMetadataStripper metadataStripper;

    public LocalStorageService(AppProperties properties, ImageMetadataStripper metadataStripper) {
        this.metadataStripper = metadataStripper;
        this.root = Paths.get(properties.storage().localRoot()).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(properties.storage().publicBaseUrl());
        try {
            Files.createDirectories(root);
            log.info("Local media storage rooted at {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage root at " + root, e);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The uploaded file is empty");
        }
        String extension = extensionFor(file.getContentType(), file.getOriginalFilename());
        String key = folder + "/" + UUID.randomUUID() + extension;
        Path target = resolveInsideRoot(key);

        long storedBytes;
        try {
            Files.createDirectories(target.getParent());
            /*
             * Images are read fully and stripped of metadata before anything is written.
             * EXIF on a phone photo carries the GPS position it was taken at, and these
             * files are served from a public path - writing the original bytes publishes
             * the user's location. Non-images stream straight through as before, since
             * there is no container we understand well enough to edit safely.
             */
            if (metadataStripper.canStrip(file.getContentType())) {
                byte[] cleaned = metadataStripper.strip(file.getBytes(), file.getContentType());
                Files.write(target, cleaned);
                storedBytes = cleaned.length;
            } else {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                storedBytes = file.getSize();
            }
        } catch (IOException e) {
            log.error("Failed writing upload to {}", target, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not store the file");
        }

        Dimensions dimensions = readDimensions(target, file.getContentType());
        return new StoredFile(key, publicBaseUrl + "/" + key, file.getContentType(), storedBytes,
                dimensions.width(), dimensions.height());
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveInsideRoot(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete stored object {}", storageKey, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolveInsideRoot(storageKey);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("File", storageKey);
        }
        try {
            return new UrlResource(path.toUri());
        } catch (IOException e) {
            throw new ResourceNotFoundException("File", storageKey);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolveInsideRoot(storageKey));
    }

    /** Rejects anything that escapes the storage root, however it was encoded. */
    private Path resolveInsideRoot(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            log.warn("Blocked path traversal attempt for key {}", storageKey);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid file reference");
        }
        return resolved;
    }

    private String extensionFor(String contentType, String originalName) {
        String byType = switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/heic" -> ".heic";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4" -> ".m4a";
            case "audio/webm" -> ".weba";
            case "video/mp4" -> ".mp4";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
        if (StringUtils.hasText(byType)) {
            return byType;
        }
        String ext = StringUtils.getFilenameExtension(originalName);
        return StringUtils.hasText(ext) && ext.matches("[A-Za-z0-9]{1,5}") ? "." + ext.toLowerCase(Locale.ROOT) : "";
    }

    private Dimensions readDimensions(Path path, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            return new Dimensions(null, null);
        }
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(in);
            return image == null ? new Dimensions(null, null)
                    : new Dimensions(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            return new Dimensions(null, null);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record Dimensions(Integer width, Integer height) {
    }
}
