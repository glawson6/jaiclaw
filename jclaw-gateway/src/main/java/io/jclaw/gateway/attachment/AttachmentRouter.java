package io.jclaw.gateway.attachment;

import io.jclaw.channel.AttachmentPayload;
import io.jclaw.channel.ChannelMessage;
import io.jclaw.core.tenant.TenantContext;

/**
 * SPI for routing file attachments received from channel messages to the
 * appropriate processing pipeline (document ingestion, media analysis, etc.).
 */
public interface AttachmentRouter {

    /**
     * Route an attachment for processing.
     *
     * @param attachment the extracted attachment payload
     * @param context    the originating channel message (for session/metadata context)
     * @param tenant     the current tenant context (may be null in single-tenant mode)
     */
    void route(AttachmentPayload attachment, ChannelMessage context, TenantContext tenant);
}
