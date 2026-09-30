package io.jaiclaw.gateway.approval;

import io.jaiclaw.core.api.Experimental;

import java.util.Optional;

/**
 * Decides who is asked to authorise a tool call.
 *
 * <p>An SPI rather than a fixed lookup because "who approves this" is a
 * deployment policy. A single-owner assistant has one answer; a team
 * deployment may route by tenant, by tool, or to an on-call rota. The
 * framework ships the single-approver case and leaves the rest open.
 *
 * <p>Returning {@link Optional#empty()} means no approver could be
 * determined, which callers treat as a <strong>denial</strong> — never as
 * permission to proceed unapproved.
 */
@Experimental
public interface ApproverResolver {

    /**
     * @param sessionKey the session whose tool call needs approval, in
     *                   {@code agentId:channel:account:peer} form; available for
     *                   routing decisions, but an implementation is free to
     *                   ignore it and always return the configured owner
     * @return the approver to ask, or empty to deny
     */
    Optional<Approver> resolve(String sessionKey);
}
