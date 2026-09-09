package io.jaiclaw.learning.review;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.hook.event.AgentEndedEvent;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.learning.LearningProperties;
import io.jaiclaw.learning.apply.ProposalApplier;
import io.jaiclaw.learning.proposal.Proposal;
import io.jaiclaw.learning.proposal.ProposalService;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a learning review after a turn ends, off the caller's thread.
 *
 * <p>Three properties this class exists to guarantee:
 *
 * <ol>
 *   <li><strong>Never on the caller's thread.</strong> The review is an LLM call;
 *       running it inline would add its latency to the user's reply.</li>
 *   <li><strong>Never mutating the live session.</strong> The reviewer only reads
 *       a rendered transcript. Appending to the session would invalidate the
 *       provider's cached prompt prefix and make every later turn more expensive.</li>
 *   <li><strong>One review per session at a time.</strong> A session that ends
 *       turns rapidly must not stack concurrent reviews of itself.</li>
 * </ol>
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public class ReviewTrigger implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(ReviewTrigger.class);
    private static final int PRIORITY = 100;

    private final LearningReviewer reviewer;
    private final ProposalService proposals;
    private final TranscriptSourceAdapter transcripts;
    private final ReviewCadenceGate cadenceGate;
    private final LearningProperties properties;
    private final ProposalApplier applier;

    /** Sessions with a review in flight, so rapid turns do not stack reviews. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ReviewTrigger(LearningReviewer reviewer,
                         ProposalService proposals,
                         TranscriptSourceAdapter transcripts,
                         ReviewCadenceGate cadenceGate,
                         LearningProperties properties,
                         ProposalApplier applier) {
        this.reviewer = reviewer;
        this.proposals = proposals;
        this.transcripts = transcripts;
        this.cadenceGate = cadenceGate;
        this.properties = properties;
        this.applier = applier;
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("jaiclaw-learning-review")
                .name("Learning Review Trigger")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        api.on(AgentEndedEvent.class, event -> {
            onAgentEnded(event);
            return null;
        }, PRIORITY);
    }

    void onAgentEnded(AgentEndedEvent event) {
        if (!properties.isEnabled()) return;
        String sessionKey = event.sessionKey();
        if (sessionKey == null) return;

        int turns = transcripts.messageCount(sessionKey);
        if (!cadenceGate.shouldRun(sessionKey, turns)) return;

        if (!inFlight.add(sessionKey)) {
            log.debug("Learning review already running for session {} — skipping", sessionKey);
            return;
        }
        cadenceGate.recordRun(sessionKey);

        String tenantId = currentTenantId();
        String agentId = event.agentId();

        // Carry the tenant onto the review thread; without it the reviewer would
        // file proposals with no tenant, or the wrong one.
        Runnable task = TenantContextPropagator.wrap(() ->
                runReview(tenantId, agentId, sessionKey));

        Thread.ofVirtual().name("learning-review-" + shortKey(sessionKey)).start(task);
    }

    private void runReview(String tenantId, String agentId, String sessionKey) {
        try {
            Optional<String> transcript = transcripts.transcript(sessionKey);
            if (transcript.isEmpty()) return;

            ReviewInput input = new ReviewInput(tenantId, agentId, sessionKey,
                    transcript.get(), List.of(), null);
            ReviewOutcome outcome = reviewer.review(input);
            if (outcome.isEmpty()) {
                log.debug("Learning review of {} found nothing to propose", sessionKey);
                return;
            }

            for (Proposal proposal : outcome.proposals()) {
                Optional<Proposal> stored = proposals.submit(proposal);
                // In auto mode, apply straight away. The applier decides which
                // kinds are eligible — patches stay manual unless explicitly allowed.
                if (stored.isPresent() && properties.isAuto() && applier != null) {
                    applier.applyIfEligible(stored.get(), "auto");
                }
            }
        } catch (RuntimeException e) {
            // A failed review must never surface to the user or kill the thread pool.
            log.warn("Learning review failed for session {}", sessionKey, e);
        } finally {
            inFlight.remove(sessionKey);
        }
    }

    /** True while a review is running for this session; for tests and diagnostics. */
    public boolean isReviewing(String sessionKey) {
        return inFlight.contains(sessionKey);
    }

    private static String currentTenantId() {
        var ctx = TenantContextHolder.get();
        return ctx == null ? "default" : ctx.getTenantId();
    }

    private static String shortKey(String sessionKey) {
        return sessionKey.length() <= 24 ? sessionKey : sessionKey.substring(sessionKey.length() - 24);
    }
}
