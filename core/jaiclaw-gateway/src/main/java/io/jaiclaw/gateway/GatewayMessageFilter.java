package io.jaiclaw.gateway;

import io.jaiclaw.channel.ChannelMessageHandler;

/**
 * A message filter inserted into the gateway message processing chain between
 * channel adapters and the {@link GatewayService}.
 *
 * <p>Implementations intercept inbound {@link io.jaiclaw.channel.ChannelMessage}s
 * before they reach the GatewayService, enabling authorization, rate limiting,
 * human-in-the-loop approval replies, or other cross-cutting concerns.
 *
 * <p>When one or more beans implementing this interface are present, the
 * auto-configuration creates a {@link FilteredGatewayLifecycle} instead of the
 * default {@link GatewayLifecycle}, routing all channel adapter messages through
 * the filter chain.
 *
 * <h2>Chaining</h2>
 *
 * <p>Filters compose. {@link FilteredGatewayLifecycle} wires them into a chain
 * at startup — each filter's downstream is the next filter, and the last one's
 * downstream is the {@link GatewayService}. Ordering follows Spring's usual
 * {@link org.springframework.core.Ordered} / {@code @Order} convention, so a
 * filter that should reject traffic early (authorization, rate limiting)
 * declares a lower order than one that consumes messages later (approval
 * replies).
 *
 * <p>Implementations must therefore:
 *
 * <ol>
 *   <li>store the handler passed to {@link #setDownstream(ChannelMessageHandler)},
 *       and</li>
 *   <li>forward messages they do not consume to it.</li>
 * </ol>
 *
 * <p>A filter that neither forwards nor consumes silently drops the message —
 * which is a legitimate outcome for an authorization failure, but is a bug
 * anywhere else.
 *
 * <p><strong>Wiring note.</strong> Before 1.3.0 the lifecycle accepted a single
 * filter and each filter had to be handed its downstream by whichever
 * auto-configuration created it. {@code setDownstream} is now part of this
 * contract so the lifecycle owns chain construction and any number of filters
 * can coexist; previously a second filter bean would have failed bean
 * resolution.
 *
 * @see FilteredGatewayLifecycle
 * @see io.jaiclaw.channel.telegram.TelegramUserIdFilter
 */
public interface GatewayMessageFilter extends ChannelMessageHandler {

    /**
     * Sets the handler that receives messages this filter passes through.
     *
     * <p>Called once during gateway startup, before any message is dispatched.
     * The supplied handler is either the next filter in the chain or the
     * {@link GatewayService} itself; a filter must not assume which.
     *
     * @param downstream the next handler in the chain; never null
     */
    void setDownstream(ChannelMessageHandler downstream);
}
