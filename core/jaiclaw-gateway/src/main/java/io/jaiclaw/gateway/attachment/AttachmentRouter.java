package io.jaiclaw.gateway.attachment;

import io.jaiclaw.channel.AttachmentPayload;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.core.tenant.TenantContext;

/**
 * SPI for routing file attachments received from channel messages to the
 * appropriate processing pipeline (document ingestion, media analysis, etc.).
 *
 * <p><b>Breaking change in 0.9.1:</b> the previous {@code void route(...)}
 * signature is replaced by {@link RouterResult} so a router can optionally
 * annotate the prompt the agent will see (e.g. a PDF extractor surfacing a
 * one-line summary) or signal that it has fully handled the attachment.
 * Existing implementations port with two lines — change the return type and
 * {@code return RouterResult.none();}.
 *
 * <p>The framework also auto-injects image and PDF attachments as Spring AI
 * {@code Media} content blocks on the agent's user message when
 * {@code jaiclaw.gateway.auto-vision=true} (default). The router still runs
 * for every attachment — auto-vision and routing are complementary, not
 * mutually exclusive.
 */
public interface AttachmentRouter {

    /**
     * Route an attachment for processing.
     *
     * @param attachment the extracted attachment payload
     * @param context    the originating channel message (for session/metadata context)
     * @param tenant     the current tenant context (may be {@code null} in single-tenant mode)
     * @return a {@link RouterResult}; use {@link RouterResult#none()} for
     *         fire-and-forget semantics that match the pre-0.9.1 default.
     */
    RouterResult route(AttachmentPayload attachment, ChannelMessage context, TenantContext tenant);
}
