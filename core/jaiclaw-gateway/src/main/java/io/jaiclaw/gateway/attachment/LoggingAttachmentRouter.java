package io.jaiclaw.gateway.attachment;

import io.jaiclaw.channel.AttachmentPayload;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default no-op attachment router that logs received attachments and returns
 * {@link RouterResult#none()}. Used when no document ingestion pipeline is
 * configured. Downstream modules (jaiclaw-documents integration, custom
 * extractors) replace this with a real router.
 *
 * <p>Note: images and PDFs are auto-injected as Spring AI {@code Media} by
 * {@code GatewayService} when {@code jaiclaw.gateway.auto-vision=true}
 * (the default). This router still runs for those — auto-vision is the
 * framework-side path; the router is for app-side annotation or routing
 * to a separate pipeline.
 */
public class LoggingAttachmentRouter implements AttachmentRouter {

    private static final Logger log = LoggerFactory.getLogger(LoggingAttachmentRouter.class);

    @Override
    public RouterResult route(AttachmentPayload attachment, ChannelMessage context, TenantContext tenant) {
        log.info("Attachment received but no processing pipeline configured: file={}, type={}, size={} bytes, channel={}, tenant={}",
                attachment.filename(),
                attachment.type(),
                attachment.sizeBytes(),
                context.channelId(),
                tenant != null ? tenant.getTenantId() : "none");
        return RouterResult.none();
    }
}
