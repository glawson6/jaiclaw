package io.jclaw.channel;

/**
 * Callback invoked by a {@link ChannelAdapter} when an inbound message arrives.
 * The gateway implements this to route messages to the agent runtime.
 */
@FunctionalInterface
public interface ChannelMessageHandler {

    /**
     * Handle an inbound message from a channel.
     *
     * @param message the normalized inbound message
     */
    void onMessage(ChannelMessage message);
}
