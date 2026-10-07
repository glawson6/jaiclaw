package io.jaiclaw.gateway.approval;

import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.core.agent.ApprovalReplyParser;
import io.jaiclaw.core.agent.PendingApprovalRegistry;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.gateway.GatewayMessageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
 *   <li>at least one live approval request exists for that conversation,</li>
 *   <li>the conversation is a configured approver's, and — when the approver
 *       is configured with a {@code user-id} — the message's sender is that
 *       user, and</li>
 *   <li>the message parses as an approve/deny answer.</li>
 * </ol>
 *
 * <p>Anything else is passed downstream untouched. That matters: an approver
 * who replies "what does that do?" should get an answer from the agent, not
 * silence, and their pending request should stay open. Consuming every message
 * from a conversation with an open request would make the approver mute until
 * the window closed.
 *
 * <h2>What gets redeemed</h2>
 *
 * <p>Only an answer that names the request's code ({@code yes K7Q4}). A bare
 * "yes" is consumed — it is clearly an answer to us — but redeems nothing; the
 * approver is told which codes are open so they can answer precisely. See
 * {@link PendingApprovalRegistry} for why the code is mandatory.
 *
 * <h2>Sender identity</h2>
 *
 * <p>Adapters publish the platform user id of the sender as
 * {@code platformData["sender_id"]}. On Telegram and Discord the message's
 * {@code peerId} is the <em>conversation</em>, which may be a group; checking
 * only the conversation would let any member approve. When an approver has a
 * {@code user-id}, a reply from anyone else is treated as an ordinary message.
 * If the channel does not expose a sender id at all, the check <strong>fails
 * closed</strong>: the reply is not honoured.
 *
 * <p>Ordered after authorization filters — there is no reason to match a reply
 * from a peer whose traffic is about to be rejected anyway.
 */
@Experimental
public class ApprovalReplyFilter implements GatewayMessageFilter {

    /** Key under which adapters publish the sender's platform user id. */
    public static final String SENDER_ID_KEY = "sender_id";

    private static final Logger log = LoggerFactory.getLogger(ApprovalReplyFilter.class);

    private final PendingApprovalRegistry pending;
    private final ApproverResolver approvers;
    private final ChannelRegistry channels;
    private ChannelMessageHandler downstream;

    /**
     * @param pending   the registry shared with {@link ChatApprovalHandler}
     * @param approvers who may answer; {@code null} accepts any conversation with a pending request
     * @param channels  used to tell the approver what is missing from a reply; {@code null} disables that
     */
    public ApprovalReplyFilter(PendingApprovalRegistry pending,
                               ApproverResolver approvers,
                               ChannelRegistry channels) {
        this.pending = pending;
        this.approvers = approvers;
        this.channels = channels;
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

        String peerKey = PendingApprovalRegistry.peerKey(message.channelId(), message.peerId());

        // Cheap guard first: most traffic has no approval outstanding, and
        // parsing every message would be wasted work.
        if (!pending.hasPending(peerKey)) {
            passThrough(message);
            return;
        }

        if (!isFromApprover(message)) {
            passThrough(message);
            return;
        }

        Optional<ApprovalReplyParser.Reply> parsed = ApprovalReplyParser.parse(message.content());
        if (parsed.isEmpty()) {
            // Not an answer. Leave the request open — the approver may be asking
            // a clarifying question, and the window is the backstop.
            log.debug("Message from {}:{} is not an approval answer — passing through",
                    message.channelId(), message.peerId());
            passThrough(message);
            return;
        }
        ApprovalReplyParser.Reply reply = parsed.get();

        if (!reply.hasCode()) {
            // An answer, but to which question? Never guess. Consumed so the
            // agent does not reply "ok!" to a bare "yes"; the approver is told
            // what to include instead.
            log.info("Approval answer from {}:{} carried no code — not applied",
                    message.channelId(), message.peerId());
            tell(message, "Which request? Include the code — " + openCodes(peerKey));
            return;
        }

        Optional<PendingApprovalRegistry.PendingApproval> redeemed =
                pending.redeem(peerKey, reply.code(), reply.decision());

        if (redeemed.isEmpty()) {
            // Unknown code, a code from another conversation, expired, or lost a
            // race. The answer was aimed at us, so consume it and say so.
            log.info("Approval answer from {}:{} named code {} with nothing to redeem",
                    message.channelId(), message.peerId(), reply.code());
            tell(message, "No open approval request has code " + reply.code()
                    + (pending.hasPending(peerKey) ? ". Open: " + openCodes(peerKey) : "."));
            return;
        }

        PendingApprovalRegistry.PendingApproval approval = redeemed.get();
        log.info("Tool '{}' {} by approver {}:{} (code {}, tenant {})",
                approval.toolName(), reply.isApproved() ? "APPROVED" : "DENIED",
                message.channelId(), message.peerId(), approval.approvalId(),
                approval.tenantId() == null ? "-" : approval.tenantId());

        // Consumed: the message was an answer, not a new request. Passing it on
        // would have the agent reply "ok!" to a bare "yes".
    }

    /**
     * True when this message may answer an approval: it comes from a configured
     * approver conversation and, where that approver restricts the user, from
     * that user.
     */
    private boolean isFromApprover(ChannelMessage message) {
        if (approvers == null) {
            return true;
        }
        List<Approver> matching = approvers.all().stream()
                .filter(a -> a.channelId().equals(message.channelId())
                        && a.peerId().equals(message.peerId()))
                .toList();
        if (matching.isEmpty()) {
            log.debug("Pending approval exists for {}:{} but it is not a configured approver conversation",
                    message.channelId(), message.peerId());
            return false;
        }
        String senderId = senderIdOf(message);
        for (Approver approver : matching) {
            if (!approver.restrictsUser()) {
                return true;
            }
            if (senderId != null && senderId.equals(approver.userId())) {
                return true;
            }
        }
        if (senderId == null) {
            log.warn("Approver {}:{} restricts replies to user-id but channel '{}' exposes no "
                            + "sender id — reply not honoured (fail closed)",
                    message.channelId(), message.peerId(), message.channelId());
        } else {
            log.warn("Reply in approver conversation {}:{} came from user {} who is not the "
                            + "configured approver — treated as an ordinary message",
                    message.channelId(), message.peerId(), senderId);
        }
        return false;
    }

    static String senderIdOf(ChannelMessage message) {
        Object raw = message.platformData() == null ? null : message.platformData().get(SENDER_ID_KEY);
        if (raw == null) return null;
        String s = String.valueOf(raw).strip();
        return s.isEmpty() ? null : s;
    }

    private String openCodes(String peerKey) {
        return pending.pendingFor(peerKey).stream()
                .map(p -> p.approvalId() + " (" + p.toolName() + ")")
                .collect(Collectors.joining(", "));
    }

    /** Best-effort reply to the approver; never affects the verdict. */
    private void tell(ChannelMessage inbound, String text) {
        if (channels == null) return;
        try {
            Optional<ChannelAdapter> adapter = channels.get(inbound.channelId());
            if (adapter.isEmpty()) return;
            adapter.get().sendMessage(ChannelMessage.outbound(
                    UUID.randomUUID().toString(),
                    inbound.channelId(),
                    inbound.accountId(),
                    inbound.peerId(),
                    text));
        } catch (RuntimeException e) {
            log.debug("Could not send approval hint to {}:{}", inbound.channelId(), inbound.peerId(), e);
        }
    }

    private void passThrough(ChannelMessage message) {
        if (downstream != null) {
            downstream.onMessage(message);
        } else {
            log.warn("No downstream handler configured for ApprovalReplyFilter — dropping message");
        }
    }
}
