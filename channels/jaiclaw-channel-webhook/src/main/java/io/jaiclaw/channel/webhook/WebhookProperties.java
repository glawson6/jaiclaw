package io.jaiclaw.channel.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Webhook channel configuration, bound from {@code jaiclaw.channels.webhook}.
 *
 * <p>Off by default, and inert with no routes configured.
 *
 * @param enabled whether the channel adapter starts
 * @param routes  configured inbound routes
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
@ConfigurationProperties(prefix = "jaiclaw.channels.webhook")
public record WebhookProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue List<WebhookRoute> routes
) {
    public WebhookProperties {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public static WebhookProperties defaults() {
        return new WebhookProperties(false, List.of());
    }
}
