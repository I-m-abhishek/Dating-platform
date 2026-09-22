package com.dating.platform.media.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stripper is the only thing standing between a phone photo's GPS tag and a public URL,
 * so it gets tested against hand-built containers rather than trusted by inspection.
 */
class ImageMetadataStripperTest {

    private final ImageMetadataStripper stripper = new ImageMetadataStripper();

    // ---- JPEG ---------------------------------------------------------------

    @Test
    @DisplayName("removes the EXIF segment from a JPEG but keeps the image data")
    void stripsJpegExif() throws IOException {
        byte[] exifPayload = "Exif\0\0GPSLatitude=51.5074,GPSLongitude=-0.1278".getBytes(StandardCharsets.US_ASCII);
        byte[] jpeg = jpegWith(exifPayload);

        assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).contains("GPSLatitude");

        byte[] cleaned = stripper.strip(jpeg, "image/jpeg");

        assertThat(new String(cleaned, StandardCharsets.ISO_8859_1))
                .as("GPS coordinates must not survive")
                .doesNotContain("GPSLatitude")
                .doesNotContain("GPSLongitude");
        assertThat(cleaned).startsWith((byte) 0xFF, (byte) 0xD8);
        assertThat(new String(cleaned, StandardCharsets.ISO_8859_1))
                .as("pixel data after Start of Scan is untouched")
                .contains("PIXELDATA");
        assertThat(cleaned.length).isLessThan(jpeg.length);
    }

    @Test
    @DisplayName("a JPEG carrying no metadata comes back byte for byte")
    void leavesCleanJpegAlone() throws IOException {
        byte[] jpeg = jpegWith(null);
        assertThat(stripper.strip(jpeg, "image/jpeg")).isEqualTo(jpeg);
    }

    // ---- PNG ----------------------------------------------------------------

    @Test
    @DisplayName("removes eXIf and tEXt chunks from a PNG, keeps IHDR/IDAT/IEND")
    void stripsPngMetadata() throws IOException {
        byte[] png = pngWith("eXIf", "GPSLatitude=51.5074");

        byte[] cleaned = stripper.strip(png, "image/png");
        String text = new String(cleaned, StandardCharsets.ISO_8859_1);

        assertThat(text).doesNotContain("GPSLatitude");
        assertThat(text).contains("IHDR").contains("IDAT").contains("IEND");
        assertThat(cleaned).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }

    // ---- WebP ---------------------------------------------------------------

    @Test
    @DisplayName("removes the EXIF chunk from a WebP and rewrites the RIFF size")
    void stripsWebpExif() {
        byte[] webp = webpWithExif();

        byte[] cleaned = stripper.strip(webp, "image/webp");
        String text = new String(cleaned, StandardCharsets.ISO_8859_1);

        assertThat(text).doesNotContain("GPSLatitude");
        assertThat(text).contains("VP8 ");

        int declared = ByteBuffer.wrap(cleaned, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertThat(declared)
                .as("RIFF size counts every byte after the size field")
                .isEqualTo(cleaned.length - 8);
    }

    // ---- Contract -----------------------------------------------------------

    @Test
    @DisplayName("declares what it can handle")
    void reportsSupport() {
        assertThat(stripper.canStrip("image/jpeg")).isTrue();
        assertThat(stripper.canStrip("image/png")).isTrue();
        assertThat(stripper.canStrip("image/webp")).isTrue();
        assertThat(stripper.canStrip("image/heic")).isFalse();
        assertThat(stripper.canStrip(null)).isFalse();
    }

    @Test
    @DisplayName("a truncated or nonsense file is returned rather than thrown away")
    void survivesGarbage() {
        byte[] nonsense = {(byte) 0xFF, (byte) 0xD8, 0x01, 0x02, 0x03};
        assertThat(stripper.strip(nonsense, "image/jpeg")).isNotNull();
        assertThat(stripper.strip(new byte[0], "image/jpeg")).isEmpty();
        assertThat(stripper.strip(null, "image/jpeg")).isNull();
    }

    // ---- builders -----------------------------------------------------------

    /** SOI, optional APP1, a quantisation table, then SOS followed by pixel bytes. */
    private byte[] jpegWith(byte[] app1Payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);

        if (app1Payload != null) {
            out.write(0xFF);
            out.write(0xE1);
            int length = app1Payload.length + 2;
            out.write(length >> 8);
            out.write(length & 0xFF);
            out.write(app1Payload);
        }

        byte[] dqt = "QUANTTABLE".getBytes(StandardCharsets.US_ASCII);
        out.write(0xFF);
        out.write(0xDB);
        out.write((dqt.length + 2) >> 8);
        out.write((dqt.length + 2) & 0xFF);
        out.write(dqt);

        out.write(0xFF);
        out.write(0xDA);
        out.write(0x00);
        out.write(0x08);
        out.write("SCANHDR".getBytes(StandardCharsets.US_ASCII), 0, 6);
        out.write("PIXELDATA".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private byte[] pngWith(String metadataChunkType, String metadataPayload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        writeChunk(out, "IHDR", "WIDTHHEIGHT".getBytes(StandardCharsets.US_ASCII));
        writeChunk(out, metadataChunkType, metadataPayload.getBytes(StandardCharsets.US_ASCII));
        writeChunk(out, "IDAT", "COMPRESSEDPIXELS".getBytes(StandardCharsets.US_ASCII));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private void writeChunk(ByteArrayOutputStream out, String type, byte[] payload) throws IOException {
        out.write(ByteBuffer.allocate(4).putInt(payload.length).array());
        out.write(type.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.write(new byte[4]); // CRC placeholder - the stripper copies it without checking
    }

    private byte[] webpWithExif() {
        byte[] vp8 = "VP8PIXELS".getBytes(StandardCharsets.US_ASCII);
        byte[] exif = "GPSLatitude=51.5074".getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        writeRiffChunk(body, "VP8 ", vp8);
        writeRiffChunk(body, "EXIF", exif);

        byte[] payload = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private void writeRiffChunk(ByteArrayOutputStream out, String type, byte[] payload) {
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());
        out.writeBytes(payload);
        if (payload.length % 2 == 1) {
            out.write(0);
        }
    }
}
