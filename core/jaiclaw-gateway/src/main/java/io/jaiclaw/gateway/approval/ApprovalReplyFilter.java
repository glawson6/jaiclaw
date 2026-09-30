package io.jaiclaw.gateway.approval;

import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.core.agent.ApprovalReplyParser;
import io.jaiclaw.core.agent.PendingApprovalRegistry;
import io.jaiclaw.core.agent.ToolApprovalDecision;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.gateway.GatewayMessageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Intercepts an inbound message that answers a pending approval request, and
 * completes the waiting tool call instead of starting a new agent turn.
 *
 * <p>Runs as a {@link GatewayMessageFilter} so it sees traffic before the
 * {@code GatewayService}. Without this the approver's "yes" would be handled as
 * an ordinary message — the agent would answer it conversationally while the
 * tool call it was meant to authorise sat waiting until it timed out.
 *
 * <h2>What gets consumed</h2>
 *
 * <p>A message is consumed only when <em>all</em> of the following hold:
 *
 * <ol>
 *   <li>a live, unexpired approval request exists for that conversation, and</li>
 *   <li>the message parses as an approve/deny answer.</li>
 * </ol>
 *
 * <p>Anything else is passed downstream untouched. That matters: an approver
 * who replies "what does that do?" should get an answer from the agent, not
 * silence, and their pending request should stay open. Consuming every message
 * from a conversation with an open request would make the approver mute until
 * the window closed.
 *
 * <p>Ordered after authorization filters — there is no reason to match a reply
 * from a peer whose traffic is about to be rejected anyway.
 */
@Experimental
public class ApprovalReplyFilter implements GatewayMessageFilter {

    private static final Logger log = LoggerFactory.getLogger(ApprovalReplyFilter.class);

    private final PendingApprovalRegistry pending;
    private ChannelMessageHandler downstream;

    public ApprovalReplyFilter(PendingApprovalRegistry pending) {
        this.pending = pending;
    }

    @Override
    public void setDownstream(ChannelMessageHandler downstream) {
        this.downstream = downstream;
    }

    @Override
    public void onMessage(ChannelMessage message) {
        if (message == null) {
            return;
        }

        String tenantId = TenantContextHolder.get() != null
                ? TenantContextHolder.get().getTenantId()
                : null;
        String peerKey = PendingApprovalRegistry.peerKey(
                tenantId, message.channelId(), message.peerId());

        // Cheap guard first: most traffic has no approval outstanding, and
        // parsing every message would be wasted work.
        if (!pending.hasPending(peerKey)) {
            passThrough(message);
            return;
        }

        Optional<ToolApprovalDecision> verdict = ApprovalReplyParser.parse(message.content());
        if (verdict.isEmpty()) {
            // Not an answer. Leave the request open — the approver may be asking
            // a clarifying question, and the window is the backstop.
            log.debug("Message from {}:{} is not an approval answer — passing through",
                    message.channelId(), message.peerId());
            passThrough(message);
            return;
        }

        Optional<PendingApprovalRegistry.PendingApproval> redeemed =
                pending.redeem(peerKey, verdict.get());

        if (redeemed.isEmpty()) {
            // Lost a race, or it expired between the check and the redeem. The
            // waiting caller has already resolved, so this is now an ordinary
            // message rather than an answer to anything.
            log.debug("Approval for {}:{} was already resolved — passing through",
                    message.channelId(), message.peerId());
            passThrough(message);
            return;
        }

        PendingApprovalRegistry.PendingApproval approval = redeemed.get();
        boolean approved = verdict.get() instanceof ToolApprovalDecision.Approved;
        log.info("Tool '{}' {} by approver {}:{} (id {})",
                approval.toolName(), approved ? "APPROVED" : "DENIED",
                message.channelId(), message.peerId(), approval.approvalId());

        // Consumed: the message was an answer, not a new request. Passing it on
        // would have the agent reply "ok!" to a bare "yes".
    }

    private void passThrough(ChannelMessage message) {
        if (downstream != null) {
            downstream.onMessage(message);
        } else {
            log.warn("No downstream handler configured for ApprovalReplyFilter — dropping message");
        }
    }
}
