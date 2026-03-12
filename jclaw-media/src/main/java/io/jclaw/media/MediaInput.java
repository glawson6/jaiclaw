package io.jclaw.media;

import java.util.Map;

/**
 * Input for media analysis — a binary payload with metadata.
 *
 * @param data      raw bytes of the media
 * @param mimeType  MIME type (e.g. "image/jpeg", "video/mp4", "audio/mpeg")
 * @param filename  original filename
 * @param metadata  additional metadata (duration, resolution, etc.)
 */
public record MediaInput(
        byte[] data,
        String mimeType,
        String filename,
        Map<String, String> metadata
) {
    public MediaInput {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        if (mimeType == null || mimeType.isBlank()) throw new IllegalArgumentException("mimeType must not be blank");
        if (filename == null) filename = "unknown";
        if (metadata == null) metadata = Map.of();
    }

    public static MediaInput of(byte[] data, String mimeType, String filename) {
        return new MediaInput(data, mimeType, filename, Map.of());
    }

    public boolean isImage() {
        return mimeType.startsWith("image/");
    }

    public boolean isVideo() {
        return mimeType.startsWith("video/");
    }

    public boolean isAudio() {
        return mimeType.startsWith("audio/");
    }

    public long sizeBytes() {
        return data.length;
    }
}
