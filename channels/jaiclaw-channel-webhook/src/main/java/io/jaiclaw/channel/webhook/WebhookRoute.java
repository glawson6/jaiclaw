package io.jaiclaw.channel.webhook;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tool.ToolProfile;

/**
 * One configured inbound webhook.
 *
 * @param routeId     path segment: {@code POST /webhooks/{routeId}}
 * @param secret      HMAC-SHA256 shared secret. A route with no secret is
 *                    <strong>disabled</strong>, not open — see
 *                    {@link #isUsable()}.
 * @param tenantId    tenant inbound payloads are attributed to
 * @param agentId     agent that handles them
 * @param sessionMode {@code ISOLATED} (a fresh session per delivery) or
 *                    {@code PER_ROUTE} (one long-lived session)
 * @param toolProfile profile for the resulting session; clamped to
 *                    {@link ToolProfile#WEBHOOK_SAFE} and never wider
 * @param callbackUrl optional URL to POST the agent's reply back to
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
@Experimental
public record WebhookRoute(
        String routeId,
        String secret,
        String tenantId,
        String agentId,
        SessionMode sessionMode,
        ToolProfile toolProfile,
        String callbackUrl
) {
    public enum SessionMode {
        /** A fresh session per delivery — no state carries between payloads. */
        ISOLATED,
        /** One session for the route, so context accumulates across deliveries. */
        PER_ROUTE
    }

    public WebhookRoute {
        if (sessionMode == null) sessionMode = SessionMode.ISOLATED;
        if (agentId == null || agentId.isBlank()) agentId = "default";
        if (tenantId == null || tenantId.isBlank()) tenantId = "default";
        // A route can never be granted more than WEBHOOK_SAFE, whatever the YAML says.
        toolProfile = ToolProfile.narrowest(
                toolProfile == null ? ToolProfile.WEBHOOK_SAFE : toolProfile,
                ToolProfile.WEBHOOK_SAFE);
    }

    /**
     * Whether this route may accept deliveries.
     *
     * <p>A route with no secret is refused rather than accepted unverified.
     * Failing closed matters more here than convenience: the alternative is an
     * unauthenticated endpoint that hands attacker-controlled text to an agent.
     */
    public boolean isUsable() {
        return secret != null && !secret.isBlank();
    }

    /** Session key for a delivery. */
    public String sessionKeyFor(String deliveryId) {
        return sessionMode == SessionMode.PER_ROUTE
                ? agentId + ":webhook:" + routeId + ":route"
                : agentId + ":webhook:" + routeId + ":" + deliveryId;
    }
}
