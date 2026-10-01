package io.jaiclaw.gateway.approval;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Who to ask for tool approval over chat, and whether the feature is on.
 *
 * <p>Config keys:
 * <pre>{@code
 * jaiclaw:
 *   approval:
 *     chat:
 *       enabled: true
 *       approvers:
 *         - channel-id: telegram
 *           account-id: ${TELEGRAM_ACCOUNT_ID}
 *           peer-id: "9001"          # the chat to ask in
 * }</pre>
 *
 * <p>Opt-in. With {@code enabled: false} (the default) no approval beans load
 * and the gateway behaves exactly as before — registering a handler changes how
 * every {@code PROMPT_ALWAYS} tool behaves, so it is not something to acquire
 * by upgrading.
 *
 * <p><strong>Enabling this with no approvers is a misconfiguration</strong>, and
 * is reported as one at startup rather than discovered when a tool call is
 * denied. The handler denies in that state; see {@link ChatApprovalHandler}.
 *
 * <p>The timing of an approval request — how long the window is and what
 * silence means — is <em>not</em> here. It lives with the rest of the loop
 * policy at {@code jaiclaw.agent.agents.<name>.tool-loop.approval}, because it
 * is per-agent and per-tool while the approver is a deployment-wide identity.
 *
 * <p>One public constructor per {@code @ConfigurationProperties} record
 * (Boot 4 record-binder rule). Programmatic defaults live on
 * {@link #defaults()}, never on an overload.
 */
@ConfigurationProperties(prefix = "jaiclaw.approval.chat")
public record ChatApprovalProperties(
        boolean enabled,
        List<ApproverConfig> approvers
) {

    public ChatApprovalProperties {
        approvers = approvers == null ? List.of() : List.copyOf(approvers);
    }

    /**
     * One approver's address on a channel.
     *
     * <p>Nested {@code @ConfigurationProperties} record, so the
     * one-public-constructor rule applies here too — this is exactly the shape
     * that loses YAML values silently when an overload exists.
     *
     * @param channelId channel to ask on, e.g. {@code telegram}
     * @param accountId sending account on that channel
     * @param peerId    conversation to ask in; the chat id for Telegram
     */
    public record ApproverConfig(String channelId, String accountId, String peerId) {

        /** True when every field needed to actually send a message is present. */
        public boolean isComplete() {
            return isSet(channelId) && isSet(peerId);
        }

        private static boolean isSet(String s) {
            return s != null && !s.isBlank();
        }
    }

    /** Programmatic default — off, nobody configured. The binder never sees this. */
    public static ChatApprovalProperties defaults() {
        return new ChatApprovalProperties(false, List.of());
    }

    /**
     * The configured approvers as domain objects, skipping incomplete entries.
     *
     * <p>An entry missing its channel or peer cannot be messaged, so carrying it
     * forward would only produce a confusing delivery failure later. Dropping it
     * here means the startup check sees an empty list and says so plainly.
     */
    public List<Approver> toApprovers() {
        return approvers.stream()
                .filter(ApproverConfig::isComplete)
                .map(a -> new Approver(a.channelId(), a.accountId(), a.peerId()))
                .toList();
    }
}
