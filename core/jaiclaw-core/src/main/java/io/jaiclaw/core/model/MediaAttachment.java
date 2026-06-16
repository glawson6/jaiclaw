package io.jaiclaw.core.model;

/**
 * Pure-Java carrier for an image (or other binary media) attachment that
 * accompanies a {@link UserMessage}. Lives in {@code jaiclaw-core} so the
 * internal model can express media without pulling in a Spring AI
 * dependency — conversion to Spring AI's {@code org.springframework.ai.content.Media}
 * happens at the agent runtime boundary.
 *
 * <p>Used by the gateway to forward image and PDF attachments from a
 * {@link io.jaiclaw.channel.AttachmentPayload channel attachment} to the
 * agent when {@code jaiclaw.gateway.auto-vision=true} and the wired chat
 * model is vision-capable.
 *
 * @param mimeType MIME type (e.g. {@code image/jpeg}, {@code application/pdf}) — required, non-blank
 * @param bytes    raw media bytes — required, non-empty
 * @param filename original filename if known (informational; empty string when unknown)
 */
public record MediaAttachment(String mimeType, byte[] bytes, String filename) {

    public MediaAttachment {
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType must not be null or blank");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be null or empty");
        }
        if (filename == null) {
            filename = "";
        }
    }

    /** Returns {@code true} if {@link #mimeType()} starts with {@code image/}. */
    public boolean isImage() {
        return mimeType.startsWith("image/");
    }

    /** Returns {@code true} if {@link #mimeType()} is {@code application/pdf} (case-insensitive). */
    public boolean isPdf() {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }
}
