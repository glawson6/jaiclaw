package io.jaiclaw.gateway.approval;

import io.jaiclaw.core.api.Experimental;

import java.util.List;
import java.util.Optional;

/** The static single-owner case: a fixed list from configuration, first entry asked. */
@Experimental
public class ConfiguredApproverResolver implements ApproverResolver {

    private final List<Approver> approvers;

    public ConfiguredApproverResolver(List<Approver> approvers) {
        this.approvers = approvers == null ? List.of() : List.copyOf(approvers);
    }

    @Override
    public Optional<Approver> resolve(String sessionKey) {
        // First-wins rather than fan-out: two people asked the same question in
        // separate chats would race on a single-use redemption, so the loser's
        // reply would be silently discarded. A rota or quorum policy belongs in
        // a custom ApproverResolver, where that behaviour is explicit.
        return approvers.isEmpty() ? Optional.empty() : Optional.of(approvers.get(0));
    }

    @Override
    public List<Approver> all() {
        return approvers;
    }
}
