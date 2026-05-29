package io.jaiclaw.channel;

import io.jaiclaw.channel.chunking.PlatformLimits;

/**
 * SPI for messaging platform adapters. Each adapter handles one channel
 * (Telegram, Slack, Discord, etc.) and converts between platform-native
 * formats and {@link ChannelMessage}.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #start(ChannelMessageHandler)} — called once during gateway startup</li>
 *   <li>{@link #sendMessage(ChannelMessage)} — called for each outbound message</li>
 *   <li>{@link #stop()} — called during shutdown</li>
 * </ol>
 */
public interface ChannelAdapter {

    /** Unique channel identifier, e.g. "telegram", "slack", "discord". */
    String channelId();

    /** Human-readable display name. */
    String displayName();

    /**
     * Start the adapter. The handler is invoked for each inbound message
     * received from the platform.
     */
    void start(ChannelMessageHandler handler);

    /** Send an outbound message to the platform. */
    DeliveryResult sendMessage(ChannelMessage message);

    /** Stop the adapter and release resources. */
    void stop();

    /** Whether this adapter is currently running. */
    boolean isRunning();

    /** Whether this adapter supports streaming responses token-by-token. */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Whether this channel is stateless — each inbound message gets a fresh,
     * ephemeral session with no history and no persistence. Useful for batch
     * processing channels (file pipelines, webhook ingestion) where each
     * message is an independent interaction.
     */
    default boolean isStateless() {
        return false;
    }

    /**
     * Platform-specific message length limits. The gateway uses these to
     * chunk long responses before delivery via {@link io.jaiclaw.channel.chunking.MessageChunker}.
     * Override in channel adapters with platform-specific constants.
     */
    default PlatformLimits platformLimits() {
        return PlatformLimits.DEFAULT;
    }
}
