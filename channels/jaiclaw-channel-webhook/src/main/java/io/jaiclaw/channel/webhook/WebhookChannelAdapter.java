package io.jaiclaw.channel.webhook;

import io.jaiclaw.channel.AbstractChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.DeliveryResult;
import io.jaiclaw.channel.chunking.PlatformLimits;
import io.jaiclaw.channel.util.WebhookSignatureUtil;
import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A generic authenticated webhook channel: an external system POSTs a payload,
 * it is HMAC-verified, and the body is handed to an agent session running under
 * the {@code WEBHOOK_SAFE} tool profile.
 *
 * <p>Inbound only. A route may name a {@code callbackUrl} for replies, but this
 * adapter does not itself make outbound HTTP calls — {@link #doSend} reports
 * unsupported rather than silently dropping a reply, so a misconfiguration
 * surfaces instead of appearing to work.
 *
 * <p><strong>Fails closed.</strong> Unknown route, missing secret, absent or bad
 * signature all refuse. The threat model is that anyone on the network can POST
 * here, and the body reaches a language model with tools attached.
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
@Experimental
public class WebhookChannelAdapter extends AbstractChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannelAdapter.class);

    /** Header carrying the hex HMAC-SHA256 of the raw body. */
    public static final String SIGNATURE_HEADER = "X-JaiClaw-Signature";

    public static final String CHANNEL_ID = "webhook";

    private final Map<String, WebhookRoute> routes = new LinkedHashMap<>();

    public WebhookChannelAdapter(WebhookProperties properties) {
        super(CHANNEL_ID, "Webhook", PlatformLimits.DEFAULT);
        for (WebhookRoute route : properties.routes()) {
            if (route.routeId() == null || route.routeId().isBlank()) {
                log.warn("Ignoring webhook route with no routeId");
                continue;
            }
            if (!route.isUsable()) {
                // Refuse rather than accept unverified: an unauthenticated route
                // hands attacker-controlled text straight to an agent.
                log.error("Webhook route '{}' has no secret configured and is DISABLED. "
                        + "Set jaiclaw.channels.webhook.routes[].secret to enable it.", route.routeId());
                continue;
            }
            routes.put(route.routeId(), route);
        }
    }

    /** Routes accepted by this adapter. */
    public Map<String, WebhookRoute> routes() {
        return Map.copyOf(routes);
    }

    public Optional<WebhookRoute> route(String routeId) {
        return Optional.ofNullable(routes.get(routeId));
    }

    /**
     * Verifies and dispatches an inbound delivery.
     *
     * @param routeId   path segment
     * @param body      raw request body, exactly as received — the signature is
     *                  over these bytes, so it must not be re-serialised first
     * @param signature value of {@link #SIGNATURE_HEADER}
     * @return the outcome; never throws
     */
    public Result handle(String routeId, String body, String signature) {
        WebhookRoute route = routes.get(routeId);
        if (route == null) {
            // Same response as a bad signature: do not let a prober distinguish
            // "no such route" from "wrong secret".
            log.debug("Webhook delivery for unknown route '{}'", routeId);
            return Result.UNAUTHORIZED;
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook delivery for route '{}' had no signature header", routeId);
            return Result.UNAUTHORIZED;
        }
        if (!WebhookSignatureUtil.verifyHmacSha256(route.secret(), body == null ? "" : body, signature)) {
            log.warn("Webhook delivery for route '{}' failed signature verification", routeId);
            return Result.UNAUTHORIZED;
        }

        String deliveryId = UUID.randomUUID().toString();
        ChannelMessage message = ChannelMessage.inbound(
                deliveryId,
                CHANNEL_ID,
                route.routeId(),
                route.sessionMode() == WebhookRoute.SessionMode.PER_ROUTE ? "route" : deliveryId,
                body == null ? "" : body,
                Map.of(
                        "tenantId", route.tenantId(),
                        "agentId", route.agentId(),
                        "routeId", route.routeId(),
                        "toolProfile", route.toolProfile().name()));

        dispatchInbound(message);
        return Result.ACCEPTED;
    }

    @Override
    protected void doStart() {
        log.info("Webhook channel started with {} route(s): {}", routes.size(), routes.keySet());
    }

    @Override
    protected void doStop() {
        log.info("Webhook channel stopped");
    }

    @Override
    protected DeliveryResult doSend(ChannelMessage message) {
        // Report rather than silently drop, so a misrouted reply is visible.
        return new DeliveryResult.Failure("webhook_inbound_only",
                "The webhook channel is inbound only; configure a callbackUrl consumer to receive replies.",
                false);
    }

    /** Outcome of an inbound delivery. */
    public enum Result {
        /** Verified and handed to the agent. */
        ACCEPTED,
        /** Unknown route, missing signature, or verification failure. */
        UNAUTHORIZED
    }
}
