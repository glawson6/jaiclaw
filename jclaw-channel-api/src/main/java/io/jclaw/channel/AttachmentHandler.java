package io.jclaw.channel;

import java.util.List;

/**
 * SPI for extracting file attachments from channel messages.
 * Each channel adapter that supports file attachments provides an implementation.
 *
 * <p>The handler is responsible for downloading the file bytes from the
 * platform-specific location (e.g., Telegram File API, Twilio CDN, MIME
 * attachment) and wrapping them in {@link AttachmentPayload} records.
 */
public interface AttachmentHandler {

    /**
     * Whether this handler can process the given attachment type.
     */
    boolean supports(AttachmentType type);

    /**
     * Extract all attachment payloads from the given channel message.
     * Returns an empty list if the message has no attachments this handler can process.
     *
     * @param message the inbound channel message with platform-specific attachment data
     * @return extracted payloads, never null
     */
    List<AttachmentPayload> extract(ChannelMessage message);
}
