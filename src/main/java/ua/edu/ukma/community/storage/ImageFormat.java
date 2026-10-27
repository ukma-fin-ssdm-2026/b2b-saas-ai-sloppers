package ua.edu.ukma.community.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;


public enum ImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_TAG = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final int WEBP_TAG_OFFSET = 8;

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        if (startsWith(data, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, 0, RIFF) && startsWith(data, WEBP_TAG_OFFSET, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
