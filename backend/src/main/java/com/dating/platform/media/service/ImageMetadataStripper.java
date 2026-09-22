package com.dating.platform.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Removes metadata from uploaded images before they are stored.
 *
 * <p><b>Why this exists.</b> A photo taken on a phone carries EXIF, and EXIF carries GPS
 * coordinates. Profile photos are served from a public, unauthenticated path, so an
 * unstripped upload publishes the exact spot the picture was taken - very often the user's
 * home. That is a safety problem, not a privacy nicety.
 *
 * <p><b>Why not just re-encode.</b> Decoding to a {@code BufferedImage} and writing it back
 * out would drop metadata as a side effect, but it also recompresses the image (visible
 * quality loss on JPEG), costs CPU proportional to pixel count, and fails outright for
 * formats {@code ImageIO} cannot write - WebP among them. Editing the container is lossless,
 * allocation-light, and leaves the pixels byte-identical.
 *
 * <p>Anything this class does not recognise is returned untouched and logged. A caller that
 * needs a guarantee should check {@link #canStrip(String)} first rather than assume.
 */
@Slf4j
@Component
public class ImageMetadataStripper {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** PNG chunks that carry text, timestamps or EXIF. Everything else is structural. */
    private static final java.util.Set<String> PNG_METADATA_CHUNKS =
            java.util.Set.of("eXIf", "tEXt", "iTXt", "zTXt", "tIME");

    public boolean canStrip(String contentType) {
        if (contentType == null) {
            return false;
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg", "image/png", "image/webp" -> true;
            default -> false;
        };
    }

    /**
     * @return the same image with metadata removed, or the input unchanged when the format
     *         is not one this class understands
     */
    public byte[] strip(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            return data;
        }
        try {
            if (startsWith(data, JPEG_MAGIC)) {
                return stripJpeg(data);
            }
            if (startsWith(data, PNG_MAGIC)) {
                return stripPng(data);
            }
            if (isWebp(data)) {
                return stripWebp(data);
            }
        } catch (RuntimeException e) {
            // A malformed container must not cost the user their upload. The bytes are
            // already type- and magic-checked by MediaValidator; storing the original is
            // the same risk we had before this class existed.
            log.warn("Could not strip metadata from a {} upload, storing as received", contentType, e);
            return data;
        }
        log.warn("No metadata stripper for content type {} - stored as received", contentType);
        return data;
    }

    // ---- JPEG ---------------------------------------------------------------

    /**
     * Copies the JPEG segment by segment, dropping the metadata application segments.
     *
     * <p>APP1 holds EXIF (and XMP), APP2 holds ICC and FlashPix, APP13 holds IPTC/Photoshop
     * blocks. COM is a free-text comment. APP0 is kept because JFIF density lives there and
     * some decoders expect it.
     *
     * <p>Scanning stops at Start of Scan: everything after it is entropy-coded pixel data
     * with no segment structure, so it is copied verbatim.
     */
    private byte[] stripJpeg(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data[0]);
        out.write(data[1]);

        int index = 2;
        while (index + 3 < data.length) {
            if ((data[index] & 0xFF) != 0xFF) {
                break; // Not a marker where one is required - bail out and copy the rest.
            }
            int marker = data[index + 1] & 0xFF;

            if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD9)) {
                out.write(data[index]);
                out.write(data[index + 1]);
                index += 2;
                continue;
            }
            if (marker == 0xDA) { // Start of Scan - pixel data follows.
                out.write(data, index, data.length - index);
                return out.toByteArray();
            }

            int length = ((data[index + 2] & 0xFF) << 8) | (data[index + 3] & 0xFF);
            if (length < 2 || index + 2 + length > data.length) {
                break; // Corrupt length - stop trusting the structure.
            }

            boolean metadata = marker == 0xFE                       // COM
                    || (marker >= 0xE1 && marker <= 0xEF);          // APP1..APP15
            if (!metadata) {
                out.write(data, index, 2 + length);
            }
            index += 2 + length;
        }

        if (index < data.length) {
            out.write(data, index, data.length - index);
        }
        return out.toByteArray();
    }

    // ---- PNG ----------------------------------------------------------------

    /** Copies the PNG chunk by chunk, dropping the text/EXIF/time chunks. */
    private byte[] stripPng(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, PNG_MAGIC.length);

        int index = PNG_MAGIC.length;
        while (index + 8 <= data.length) {
            int length = ByteBuffer.wrap(data, index, 4).getInt();
            if (length < 0 || index + 12 + (long) length > data.length) {
                break;
            }
            String type = new String(data, index + 4, 4, StandardCharsets.US_ASCII);
            int total = 12 + length; // length + type + payload + CRC

            if (!PNG_METADATA_CHUNKS.contains(type)) {
                out.write(data, index, total);
            }
            index += total;

            if ("IEND".equals(type)) {
                return out.toByteArray();
            }
        }
        return out.toByteArray();
    }

    // ---- WebP ---------------------------------------------------------------

    private boolean isWebp(byte[] data) {
        return data.length > 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    /**
     * WebP is a RIFF container. Drops the EXIF and XMP chunks and rewrites the RIFF size,
     * which counts every byte after the size field itself.
     */
    private byte[] stripWebp(byte[] data) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(data.length);
        body.write(data, 8, 4); // "WEBP"

        int index = 12;
        while (index + 8 <= data.length) {
            String type = new String(data, index, 4, StandardCharsets.US_ASCII);
            int size = ByteBuffer.wrap(data, index + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (size < 0) {
                break;
            }
            // RIFF chunks are padded to an even length.
            int total = 8 + size + (size % 2);
            if (index + total > data.length) {
                break;
            }
            if (!"EXIF".equals(type) && !"XMP ".equals(type)) {
                body.write(data, index, total);
            }
            index += total;
        }

        byte[] payload = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        out.write(data, 0, 4); // "RIFF"
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array(), 0, 4);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
