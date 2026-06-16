package io.jaiclaw.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the JaiClaw gateway. Prefix: {@code jaiclaw.gateway}.
 *
 * @param autoVision when {@code true} (default), image and PDF attachments on
 *                   inbound {@code ChannelMessage}s are auto-injected as
 *                   Spring AI {@code Media} content blocks on the user
 *                   message sent to the agent. Operators with a non-vision
 *                   chat model should set this to {@code false} (most
 *                   providers ignore unsupported media silently, but some
 *                   may hard-error). See
 *                   {@code docs/issues/attachment-injection-gap.md}.
 */
@ConfigurationProperties(prefix = "jaiclaw.gateway")
public record GatewayProperties(@DefaultValue("true") boolean autoVision) {

    public static final GatewayProperties DEFAULT = new GatewayProperties(true);
}
