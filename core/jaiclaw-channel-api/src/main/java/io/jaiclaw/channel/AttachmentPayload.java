package io.jaiclaw.channel;

/**
 * Normalized attachment payload extracted from a channel message.
 * Contains the raw bytes and metadata for downstream processing
 * (document ingestion, media analysis, etc.).
 *
 * @param filename  Original filename from the channel platform
 * @param type      Classified attachment type
 * @param bytes     Raw file content
 * @param mimeType  MIME type (e.g. "application/pdf", "image/jpeg")
 * @param sizeBytes File size in bytes
 */
public record AttachmentPayload(
        String filename,
        AttachmentType type,
        byte[] bytes,
        String mimeType,
        long sizeBytes
) {
    public AttachmentPayload {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }

    /**
     * Create a payload, auto-detecting type from mimeType and size from bytes.
     */
    public static AttachmentPayload of(String filename, String mimeType, byte[] bytes) {
        return new AttachmentPayload(
                filename,
                AttachmentType.fromMimeType(mimeType),
                bytes,
                mimeType,
                bytes.length
        );
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String filename;
        private AttachmentType type;
        private byte[] bytes;
        private String mimeType;
        private long sizeBytes;

        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder type(AttachmentType type) { this.type = type; return this; }
        public Builder bytes(byte[] bytes) { this.bytes = bytes; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder sizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; return this; }

        public AttachmentPayload build() {
            return new AttachmentPayload(filename, type, bytes, mimeType, sizeBytes);
        }
    }
}
