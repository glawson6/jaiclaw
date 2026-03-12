package io.jclaw.channel;

/**
 * Classification of file attachments received from messaging channels.
 */
public enum AttachmentType {
    PDF,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT;

    /**
     * Infer the attachment type from a MIME type string.
     */
    public static AttachmentType fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return DOCUMENT;
        }
        String lower = mimeType.toLowerCase();
        if (lower.equals("application/pdf")) return PDF;
        if (lower.startsWith("image/")) return IMAGE;
        if (lower.startsWith("video/")) return VIDEO;
        if (lower.startsWith("audio/")) return AUDIO;
        return DOCUMENT;
    }
}
