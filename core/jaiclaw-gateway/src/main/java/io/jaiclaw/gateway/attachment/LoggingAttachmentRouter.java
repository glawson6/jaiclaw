package io.jaiclaw.gateway.attachment;

import io.jaiclaw.channel.AttachmentPayload;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default no-op attachment router that logs received attachments.
 * This is used when no document ingestion pipeline is configured.
 * Downstream modules (jaiclaw-documents integration) replace this with a real router.
 */
public class LoggingAttachmentRouter implements AttachmentRouter {

    private static final Logger log = LoggerFactory.getLogger(LoggingAttachmentRouter.class);

    @Override
    public void route(AttachmentPayload attachment, ChannelMessage context, TenantContext tenant) {
        log.info("Attachment received but no processing pipeline configured: file={}, type={}, size={} bytes, channel={}, tenant={}",
                attachment.filename(),
                attachment.type(),
                attachment.sizeBytes(),
                context.channelId(),
                tenant != null ? tenant.getTenantId() : "none");
    }
}
