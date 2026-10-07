package io.jaiclaw.gateway.approval;

import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.core.agent.ApprovalPolicy;
import io.jaiclaw.core.agent.PendingApprovalRegistry;
import io.jaiclaw.core.agent.ToolApprovalDecision;
import io.jaiclaw.core.agent.ToolApprovalHandler;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asks a human for tool approval over a chat channel and waits for a text
 * reply.
 *
 * <p>The first {@link ToolApprovalHandler} that actually uses the SPI's
 * asynchronous contract. The two shipped console handlers block on
 * {@code readLine()} and return an already-completed future, so they are inert
 * in any deployment without a TTY — which is every server deployment.
 *
 * <h2>Why text and not buttons</h2>
 *
 * <p>Inline keyboards would remove the parsing ambiguity, but the Telegram
 * adapter has no {@code reply_markup} support, drops {@code callback_query}
 * updates before they reach the gateway, and discards {@code platformData} on
 * send. Text replies need none of that, work on every channel that can carry a
 * message, and are what an approver reaches for anyway.
 *
 * <h2>Binding the answer to the question</h2>
 *
 * <p>Every request carries a short code, shown in the prompt, which the approver
 * must echo back ({@code yes K7Q4}). A bare "yes" redeems nothing. Without the
 * code, a reply meant for one request could be applied to whichever request
 * happened to be newest — and a prompt-injected model that emits a benign call
 * followed by a dangerous one would get one "yes" applied to the dangerous one.
 *
 * <h2>Routing</h2>
 *
 * <p>The question goes to a configured approver, not to whoever triggered the
 * run. An agent asking its own requester for permission is not a control: the
 * person who typed "reboot it" is the last person who should confirm it, and a
 * cron-triggered or API-triggered run has no requester to ask at all.
 *
 * <p>When no approver is configured, this handler <strong>denies</strong>
 * rather than guessing — matching the loop's fail-closed stance that an
 * approval which cannot be obtained is not an approval.
 */
@Experimental
public class ChatApprovalHandler implements ToolApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatApprovalHandler.class);

    private final ChannelRegistry channelRegistry;
    private final PendingApprovalRegistry pending;
    private final ApproverResolver approverResolver;
    private final ApprovalPolicy policy;

    public ChatApprovalHandler(ChannelRegistry channelRegistry,
                               PendingApprovalRegistry pending,
                               ApproverResolver approverResolver,
                               ApprovalPolicy policy) {
        this.channelRegistry = channelRegistry;
        this.pending = pending;
        this.approverResolver = approverResolver;
        this.policy = policy == null ? ApprovalPolicy.DEFAULT : policy;
    }

    @Override
    public CompletableFuture<ToolApprovalDecision> requestApproval(
            String toolName, Map<String, Object> parameters, String sessionKey) {

        Optional<Approver> maybeApprover = approverResolver.resolve(sessionKey);
        if (maybeApprover.isEmpty()) {
            log.warn("Tool '{}' requires approval but no approver is configured — denying. "
                            + "Set jaiclaw.approval.chat.approvers to enable chat approval.",
                    toolName);
            return CompletableFuture.completedFuture(new ToolApprovalDecision.Denied(
                    "no approver is configured to authorise '" + toolName + "'"));
        }
        Approver approver = maybeApprover.get();

        Optional<ChannelAdapter> adapter = channelRegistry.get(approver.channelId());
        if (adapter.isEmpty()) {
            log.warn("Approver channel '{}' is not registered — denying '{}'",
                    approver.channelId(), toolName);
            return CompletableFuture.completedFuture(new ToolApprovalDecision.Denied(
                    "approver channel '" + approver.channelId() + "' is unavailable"));
        }

        // Recorded for audit only. The lookup key must not depend on the tenant:
        // the reply is matched by a filter that runs before tenant resolution.
        String tenantId = TenantContextHolder.get() != null
                ? TenantContextHolder.get().getTenantId()
                : null;
        String peerKey = PendingApprovalRegistry.peerKey(approver.channelId(), approver.peerId());
        Duration window = policy.timeoutFor(toolName);

        PendingApprovalRegistry.PendingApproval request =
                pending.register(toolName, sessionKey, tenantId, peerKey, window);
        if (request.future().isDone()) {
            // Registry refused — the conversation already has too many open
            // questions. The future already carries the denial.
            log.warn("Approval for '{}' refused: too many requests outstanding for {}:{}",
                    toolName, approver.channelId(), approver.peerId());
            return request.future();
        }

        try {
            adapter.get().sendMessage(ChannelMessage.outbound(
                    UUID.randomUUID().toString(),
                    approver.channelId(),
                    approver.accountId(),
                    approver.peerId(),
                    renderPrompt(toolName, parameters, window, request.approvalId())));
        } catch (RuntimeException e) {
            // If the question never reached anyone, do not leave the caller
            // waiting out the full window for an answer that cannot come.
            pending.discard(request.approvalId());
            log.warn("Failed to deliver approval request for '{}' — denying", toolName, e);
            return CompletableFuture.completedFuture(new ToolApprovalDecision.Denied(
                    "approval request could not be delivered"));
        }

        log.info("Approval requested for '{}' from {}:{} (window {}, code {})",
                toolName, approver.channelId(), approver.peerId(), window, request.approvalId());
        return request.future();
    }

    /**
     * The message the approver sees.
     *
     * <p>Names the tool, shows the arguments, states the code to echo back, the
     * deadline, and what happens on silence — an approver cannot make a
     * judgement without knowing what is being asked or how long they have.
     */
    private String renderPrompt(String toolName, Map<String, Object> parameters,
                                Duration window, String code) {
        String action = policy.onTimeoutFor(toolName) == ApprovalPolicy.OnTimeout.APPROVE
                ? "proceed automatically"
                : "be denied";
        StringBuilder sb = new StringBuilder();
        sb.append("Approval needed: `").append(toolName).append("` — code ").append(code).append("\n");
        if (parameters != null && !parameters.isEmpty()) {
            sb.append("\n");
            parameters.forEach((k, v) -> sb.append("• ")
                    .append(k).append(": ").append(truncate(String.valueOf(v))).append("\n"));
        }
        sb.append("\nReply *yes ").append(code).append("* to approve or *no ")
                .append(code).append("* to deny.\n");
        sb.append("No reply within ").append(humanize(window))
                .append(" and it will ").append(action).append(".");
        return sb.toString();
    }

    /** Keeps one oversized argument from pushing the question off the screen. */
    private static String truncate(String value) {
        if (value == null) return "null";
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    private static String humanize(Duration d) {
        long seconds = d.toSeconds();
        if (seconds % 3600 == 0 && seconds >= 3600) return (seconds / 3600) + "h";
        if (seconds % 60 == 0 && seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }
}
