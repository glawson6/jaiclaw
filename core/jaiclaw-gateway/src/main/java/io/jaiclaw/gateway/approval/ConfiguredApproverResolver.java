package io.jaiclaw.gateway.approval;

import io.jaiclaw.core.api.Experimental;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the approver from static configuration.
 *
 * <p>The default {@link ApproverResolver}: one configured owner answers for
 * every tool call, regardless of which session triggered it. That is the
 * correct shape for a single-owner assistant, and it is deliberately
 * <em>not</em> derived from the triggering session — see
 * {@link ChatApprovalHandler} for why asking the requester is not a control.
 *
 * <p>With nothing configured this resolves to empty, which the handler treats
 * as a denial. A chat-approval deployment that forgets to name an approver
 * fails closed and says so, rather than silently letting tool calls through.
 */
@Experimental
public class ConfiguredApproverResolver implements ApproverResolver {

    private final List<Approver> approvers;

    public ConfiguredApproverResolver(List<Approver> approvers) {
        this.approvers = approvers == null ? List.of() : List.copyOf(approvers);
    }

    /**
     * @return the first configured approver, or empty when none is configured
     */
    @Override
    public Optional<Approver> resolve(String sessionKey) {
        // First-wins rather than fan-out: two people asked the same question in
        // separate chats would race, and the registry is single-use per peer, so
        // the loser's reply would be silently discarded. A rota or quorum policy
        // belongs in a custom ApproverResolver, where that behaviour is explicit.
        return approvers.isEmpty() ? Optional.empty() : Optional.of(approvers.get(0));
    }

    /** All configured approvers, for diagnostics and for the reply filter. */
    public List<Approver> all() {
        return approvers;
    }
}
