package io.jaiclaw.channel;

import io.jaiclaw.channel.chunking.MessageChunker;
import io.jaiclaw.channel.chunking.PlatformLimits;
import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for channel adapter implementations.
 *
 * <p>Pre-0.8.0 every channel re-implemented the same plumbing — a
 * {@code running} {@code AtomicBoolean}, the inbound-handler field,
 * lifecycle state transitions, message chunking, and HMAC signature
 * verification. The audit
 * ({@code docs/CODEBASE-ANALYSIS-2026-06-10.md} §3.3) called out this
 * pattern as the single highest-leverage item for community
 * contributions: the typical channel ran 250–400+ LOC of repeated
 * boilerplate.
 *
 * <p>0.8.0 consolidates that boilerplate here. Concrete adapters
 * extend {@code AbstractChannelAdapter} and implement three hooks:
 *
 * <ul>
 *   <li>{@link #doStart()} — connect to the platform (start polling
 *       thread, register webhook, open Socket Mode WebSocket, etc.).</li>
 *   <li>{@link #doStop()} — release platform-side resources.</li>
 *   <li>{@link #doSend(ChannelMessage)} — deliver a single (already
 *       chunked) message to the platform; return the platform's
 *       message id or a delivery failure.</li>
 * </ul>
 *
 * <p>The base class final-implements
 * {@link #start(ChannelMessageHandler)}, {@link #stop()},
 * {@link #isRunning()}, {@link #channelId()}, {@link #displayName()},
 * {@link #platformLimits()}, and {@link #sendMessage(ChannelMessage)}.
 * The {@code sendMessage} path automatically chunks long content via
 * {@link MessageChunker} so the platform-specific
 * {@link #doSend(ChannelMessage)} only ever sees within-limit messages.
 *
 * <p>Subclasses dispatch inbound messages via the protected
 * {@link #dispatchInbound(ChannelMessage)} helper, which guards against
 * the case where {@code start} has not yet been called (e.g., an
 * inbound webhook arriving while the gateway is still bootstrapping).
 *
 * <p>HMAC signature verification lives in
 * {@link io.jaiclaw.channel.util.WebhookSignatureUtil} — subclasses
 * that need it call its static methods rather than redoing the
 * constant-time-compare dance.
 */
@Experimental
public abstract class AbstractChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractChannelAdapter.class);

    private final String channelId;
    private final String displayName;
    private final PlatformLimits limits;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ChannelMessageHandler handler;

    protected AbstractChannelAdapter(String channelId, String displayName, PlatformLimits limits) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.channelId = channelId;
        this.displayName = displayName;
        this.limits = limits != null ? limits : PlatformLimits.DEFAULT;
    }

    // ─── Identity / capability declarations (final) ──────────────────

    @Override
    public final String channelId() {
        return channelId;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final PlatformLimits platformLimits() {
        return limits;
    }

    @Override
    public final boolean isRunning() {
        return running.get();
    }

    // ─── Lifecycle (final, delegates to subclass hooks) ──────────────

    @Override
    public final void start(ChannelMessageHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("{} adapter already running — start() is a no-op", channelId);
            return;
        }
        this.handler = handler;
        try {
            doStart();
            log.info("{} adapter started", displayName);
        } catch (RuntimeException e) {
            running.set(false);
            this.handler = null;
            throw e;
        }
    }

    @Override
    public final void stop() {
        if (!running.compareAndSet(true, false)) {
            log.debug("{} adapter already stopped — stop() is a no-op", channelId);
            return;
        }
        try {
            doStop();
        } catch (RuntimeException e) {
            log.warn("{} adapter doStop() threw: {}", channelId, e.getMessage(), e);
        } finally {
            this.handler = null;
            log.info("{} adapter stopped", displayName);
        }
    }

    // ─── Outbound (final, with auto-chunking) ────────────────────────

    /**
     * Final-implements outbound delivery with automatic chunking against
     * {@link #platformLimits()}. Each chunk is delivered via
     * {@link #doSend(ChannelMessage)}; the first chunk's success result is
     * returned as the overall delivery result.
     *
     * <p>If the adapter is not running, returns a {@code Failure} without
     * calling {@link #doSend} — short-circuits the typical race during
     * gateway shutdown.
     *
     * <p>Subclasses that need to suppress chunking (e.g., to enforce a
     * single-message-per-call API) can override this method directly.
     */
    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        if (!running.get()) {
            return new DeliveryResult.Failure("adapter_not_running",
                    displayName + " adapter is not running", false);
        }
        if (message == null) {
            return new DeliveryResult.Failure("null_message",
                    "Cannot send a null ChannelMessage", false);
        }
        String content = message.content();
        if (content == null || content.length() <= limits.maxTextLength()) {
            return doSend(message);
        }
        List<String> chunks = MessageChunker.chunk(content, limits);
        DeliveryResult firstSuccess = null;
        for (int i = 0; i < chunks.size(); i++) {
            ChannelMessage chunk = new ChannelMessage(
                    message.id(), message.channelId(), message.accountId(), message.peerId(),
                    chunks.get(i), message.timestamp(), message.direction(),
                    message.attachments(), message.platformData());
            DeliveryResult result = doSend(chunk);
            if (result instanceof DeliveryResult.Failure failure) {
                if (firstSuccess != null) {
                    // partial delivery: log + return success of the first chunk
                    log.warn("{} adapter: chunk {}/{} failed after partial delivery: {}",
                            channelId, i + 1, chunks.size(), failure.message());
                    return firstSuccess;
                }
                return failure;
            }
            if (firstSuccess == null) {
                firstSuccess = result;
            }
        }
        return firstSuccess != null ? firstSuccess : new DeliveryResult.Failure(
                "empty_chunks", "Chunking produced no output", false);
    }

    // ─── Subclass hooks ──────────────────────────────────────────────

    /** Start the platform-side machinery (polling, webhook registration, etc.). */
    protected abstract void doStart();

    /** Stop the platform-side machinery and release resources. */
    protected abstract void doStop();

    /**
     * Deliver a single platform message (already chunked to fit
     * {@link #platformLimits()}).
     *
     * @param message the message to send; {@link ChannelMessage#content()}
     *                is within the platform's max-length limit
     * @return delivery result — success carries the platform message id
     */
    protected abstract DeliveryResult doSend(ChannelMessage message);

    // ─── Protected helpers ───────────────────────────────────────────

    /**
     * Dispatch an inbound message to the registered {@link ChannelMessageHandler}.
     *
     * <p>No-op if the adapter is not running (e.g., a late-arriving webhook
     * after {@link #stop()} or before {@link #start(ChannelMessageHandler)}).
     */
    protected final void dispatchInbound(ChannelMessage message) {
        ChannelMessageHandler h = this.handler;
        if (h == null) {
            log.debug("{} adapter: dropping inbound message; no handler registered", channelId);
            return;
        }
        try {
            h.onMessage(message);
        } catch (RuntimeException e) {
            log.warn("{} adapter: inbound handler threw: {}", channelId, e.getMessage(), e);
        }
    }
}
