package io.jaiclaw.gateway;

import io.jaiclaw.channel.ChannelMessageHandler;

/**
 * Marker interface for message filters that can be inserted into the gateway
 * message processing chain between channel adapters and the {@link GatewayService}.
 *
 * <p>Implementations intercept inbound {@link io.jaiclaw.channel.ChannelMessage}s
 * before they reach the GatewayService, enabling authorization, rate limiting,
 * or other cross-cutting concerns.
 *
 * <p>When a bean implementing this interface is present, the auto-configuration
 * will create a {@link FilteredGatewayLifecycle} instead of the default
 * {@link GatewayLifecycle}, routing all channel adapter messages through the filter.
 *
 * @see FilteredGatewayLifecycle
 * @see io.jaiclaw.channel.telegram.TelegramUserIdFilter
 */
public interface GatewayMessageFilter extends ChannelMessageHandler {
}
