package io.jaiclaw.gateway.approval;

import io.jaiclaw.core.api.Experimental;

import java.util.List;
import java.util.Optional;

/**
 * Decides who is asked to approve a tool call, and who is allowed to answer.
 *
 * <p>{@link #resolve} is consulted when a question is sent; {@link #all} is
 * consulted when a reply arrives, so the reply filter can check that the
 * message really came from an approver conversation (and, when configured,
 * from the approver's own user id) before treating it as consent.
 */
@Experimental
public interface ApproverResolver {

    /** The approver to ask for this session, or empty to deny. */
    Optional<Approver> resolve(String sessionKey);

    /** Every identity whose replies may redeem an approval. */
    List<Approver> all();
}
