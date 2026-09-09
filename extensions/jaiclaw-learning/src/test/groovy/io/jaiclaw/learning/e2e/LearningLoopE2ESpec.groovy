package io.jaiclaw.learning.e2e

import io.jaiclaw.agent.session.InMemorySessionManager
import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.hook.event.AgentEndedEvent
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.model.UserMessage
import io.jaiclaw.learning.LearningProperties
import io.jaiclaw.learning.apply.MemoryProposalApplier
import io.jaiclaw.learning.proposal.JsonFileProposalStore
import io.jaiclaw.learning.proposal.MemoryProposal
import io.jaiclaw.learning.proposal.ProposalService
import io.jaiclaw.learning.proposal.ProposalState
import io.jaiclaw.learning.proposal.SkillProposal
import io.jaiclaw.learning.review.LearningReviewer
import io.jaiclaw.learning.review.ReviewCadenceGate
import io.jaiclaw.learning.review.ReviewInput
import io.jaiclaw.learning.review.ReviewOutcome
import io.jaiclaw.learning.review.ReviewTrigger
import io.jaiclaw.learning.review.TranscriptSourceAdapter
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * §5.2 row 4 of the 1.2.0 plan — the 4A learning loop end to end, with a scripted
 * reviewer (no network) and a real session manager, proposal store and applier.
 */
class LearningLoopE2ESpec extends Specification {

    @TempDir
    Path tmp

    static final String SESSION = "assistant:slack:acme:C1"

    InMemorySessionManager sessions = new InMemorySessionManager(null)
    AgentHookDispatcher hooks = Mock()
    AgentMindMemoryProvider memory = Mock()

    JsonFileProposalStore store
    ProposalService service
    TranscriptSourceAdapter transcripts
    LearningProperties props

    def setup() {
        store = new JsonFileProposalStore(tmp)
        service = new ProposalService(store)
        transcripts = new TranscriptSourceAdapter(sessions)
        props = proposeMode()
        seedSession()
    }

    private static LearningProperties proposeMode() {
        new LearningProperties("propose", 2, Duration.ZERO, 12000,
                "/tmp/x", "/tmp/y", false, true, Duration.ofDays(30), Duration.ofDays(90))
    }

    private static LearningProperties autoMode() {
        new LearningProperties("auto", 2, Duration.ZERO, 12000,
                "/tmp/x", "/tmp/y", false, true, Duration.ofDays(30), Duration.ofDays(90))
    }

    private void seedSession() {
        sessions.getOrCreate(SESSION, "assistant")
        sessions.appendMessage(SESSION, new UserMessage("u1", "how do I get a refund?", "user"))
        sessions.appendMessage(SESSION, AssistantMessage.builder().id("a1")
                .content("Look up the order, then check the 30-day window.").build())
        sessions.appendMessage(SESSION, new UserMessage("u2", "thanks — always use metric units", "user"))
        sessions.appendMessage(SESSION, AssistantMessage.builder().id("a2").content("Noted.").build())
    }

    private LearningReviewer reviewerReturning(List proposals) {
        return { ReviewInput input -> ReviewOutcome.of(proposals) } as LearningReviewer
    }

    private ReviewTrigger trigger(LearningReviewer reviewer, LearningProperties p = props,
                                  MemoryProposalApplier applier = null) {
        new ReviewTrigger(reviewer, service, transcripts,
                new ReviewCadenceGate(Duration.ZERO, 2), p, applier)
    }

    private static MemoryProposal memoryProposal() {
        new MemoryProposal(null, "acme", SESSION, Instant.now(), ProposalState.PENDING,
                "unit preference", MemoryScope.AGENT, "Preferences", "Always use metric units")
    }

    private static SkillProposal skillProposal() {
        new SkillProposal(null, "acme", SESSION, Instant.now(), ProposalState.PENDING,
                "refund workflow", "refund-flow", "Handles refunds",
                "1. Look up the order\n2. Check the 30-day window")
    }

    private void awaitProposals(int expected, String tenant = "acme") {
        for (int i = 0; i < 300; i++) {
            if (service.list(tenant).size() >= expected) return
            Thread.sleep(10)
        }
    }

    // ── Review produces proposals ────────────────────────────────────────────

    def "a finished turn produces proposals from the session transcript"() {
        given:
        def captured = null
        def reviewer = { ReviewInput input ->
            captured = input
            ReviewOutcome.of([memoryProposal(), skillProposal()])
        } as LearningReviewer

        when:
        trigger(reviewer).onAgentEnded(AgentEndedEvent.of("assistant", SESSION,
                AssistantMessage.builder().id("x").content("done").build()))
        awaitProposals(2)

        then: "the reviewer saw the real conversation"
        captured != null
        captured.transcript().contains("how do I get a refund?")
        captured.transcript().contains("Always use metric units") ||
                captured.transcript().contains("always use metric units")

        and: "and both proposals were filed"
        service.list("acme").size() == 2
    }

    // ── The cache-safety invariant ───────────────────────────────────────────

    def "a review never mutates the live session"() {
        given: "a snapshot of the session before any review runs"
        def before = sessions.get(SESSION).get().messages().collect { it.id() }
        def beforeCount = before.size()

        when:
        trigger(reviewerReturning([memoryProposal()])).onAgentEnded(
                AgentEndedEvent.of("assistant", SESSION,
                        AssistantMessage.builder().id("x").content("done").build()))
        awaitProposals(1)

        then: "the message list is byte-identical — appending would invalidate the prompt cache"
        def after = sessions.get(SESSION).get().messages().collect { it.id() }
        after == before
        after.size() == beforeCount
    }

    def "the transcript adapter is read-only"() {
        given:
        def before = sessions.get(SESSION).get().messages().size()

        when:
        3.times { transcripts.transcript(SESSION) }

        then:
        sessions.get(SESSION).get().messages().size() == before
    }

    def "system messages are excluded from the transcript"() {
        when:
        def text = transcripts.transcript(SESSION).get()

        then: "feeding the agent its own instructions back invites it to 'learn' them"
        !text.contains("system:")
        text.contains("user:")
        text.contains("assistant:")
    }

    // ── propose vs auto ──────────────────────────────────────────────────────

    def "propose mode files a proposal but writes nothing"() {
        when:
        trigger(reviewerReturning([memoryProposal()])).onAgentEnded(
                AgentEndedEvent.of("assistant", SESSION,
                        AssistantMessage.builder().id("x").content("done").build()))
        awaitProposals(1)

        then:
        service.list("acme", ProposalState.PENDING).size() == 1
        0 * memory.saveMemory(_)
    }

    def "auto mode applies a memory proposal immediately"() {
        given:
        def auto = autoMode()
        def applier = new MemoryProposalApplier(memory, service, auto, hooks)
        memory.findMemory(_, _, _, _) >> Optional.empty()
        memory.saveMemory(_) >> { MemoryDocument d -> d }

        when:
        trigger(reviewerReturning([memoryProposal()]), auto, applier).onAgentEnded(
                AgentEndedEvent.of("assistant", SESSION,
                        AssistantMessage.builder().id("x").content("done").build()))
        awaitProposals(1)
        for (int i = 0; i < 200; i++) {
            if (!service.list("acme", ProposalState.APPLIED).isEmpty()) break
            Thread.sleep(10)
        }

        then:
        service.list("acme", ProposalState.APPLIED).size() == 1
    }

    // ── Applying ─────────────────────────────────────────────────────────────

    def "applying a memory proposal appends under its heading and marks it APPLIED"() {
        given:
        def applier = new MemoryProposalApplier(memory, service, props, hooks)
        def p = service.submit(memoryProposal()).get()
        def saved = null
        memory.findMemory("acme", MemoryScope.AGENT, "assistant", null) >> Optional.empty()
        memory.saveMemory(_) >> { MemoryDocument d -> saved = d; d }

        when:
        def result = applier.applyIfEligible(p, "operator")

        then:
        result.applied()
        saved.content().contains("Preferences")
        saved.content().contains("Always use metric units")
        store.find("acme", p.id()).get().state() == ProposalState.APPLIED
    }

    def "existing memory is appended to, never replaced"() {
        given: "memory the user has accumulated over months"
        def applier = new MemoryProposalApplier(memory, service, props, hooks)
        def p = service.submit(memoryProposal()).get()
        def existing = MemoryDocument.forAgent("acme", "assistant", "## Earlier\n\nimportant history", 8000)
        def saved = null
        memory.findMemory(_, _, _, _) >> Optional.of(existing)
        memory.saveMemory(_) >> { MemoryDocument d -> saved = d; d }

        when:
        applier.applyIfEligible(p, "operator")

        then: "a learning pass must never destroy more than it adds"
        saved.content().contains("important history")
        saved.content().contains("Always use metric units")
    }

    def "a failed write leaves the proposal PENDING rather than claiming success"() {
        given:
        def applier = new MemoryProposalApplier(memory, service, props, hooks)
        def p = service.submit(memoryProposal()).get()
        memory.findMemory(_, _, _, _) >> Optional.empty()
        memory.saveMemory(_) >> { throw new IllegalStateException("disk full") }

        when:
        def result = applier.applyIfEligible(p, "operator")

        then:
        !result.applied()
        result.message().contains("disk full")
        store.find("acme", p.id()).get().state() == ProposalState.PENDING
    }

    def "with no memory provider, memory proposals refuse clearly instead of failing startup"() {
        given:
        def applier = new MemoryProposalApplier(null, service, props, hooks)
        def p = service.submit(memoryProposal()).get()

        when:
        def result = applier.applyIfEligible(p, "operator")

        then:
        !result.applied()
        result.message().contains("No memory provider")
    }

    def "skill proposals are refused with a clear message in 4A"() {
        given:
        def applier = new MemoryProposalApplier(memory, service, props, hooks)
        def p = service.submit(skillProposal()).get()

        when:
        def result = applier.applyIfEligible(p, "operator")

        then:
        !result.applied()
        result.message().contains("skill workshop")
    }

    // ── Cadence and concurrency ──────────────────────────────────────────────

    def "the cadence gate blocks a second review inside the window"() {
        given:
        def gate = new ReviewCadenceGate(Duration.ofMinutes(5), 2)

        expect:
        gate.shouldRun(SESSION, 4)

        when:
        gate.recordRun(SESSION)

        then: "a chatty session must not trigger an LLM review every turn"
        !gate.shouldRun(SESSION, 4)
    }

    def "the cadence gate requires a minimum number of turns"() {
        given:
        def gate = new ReviewCadenceGate(Duration.ZERO, 4)

        expect:
        !gate.shouldRun(SESSION, 3)
        gate.shouldRun(SESSION, 4)
    }

    def "learning off means no review runs at all"() {
        given:
        def off = LearningProperties.defaults()
        def called = false
        def reviewer = { ReviewInput i -> called = true; ReviewOutcome.empty() } as LearningReviewer

        when:
        trigger(reviewer, off).onAgentEnded(AgentEndedEvent.of("assistant", SESSION,
                AssistantMessage.builder().id("x").content("done").build()))
        Thread.sleep(100)

        then:
        !called
        service.list("acme").isEmpty()
    }

    def "a reviewer that throws never surfaces to the caller"() {
        given:
        def reviewer = { ReviewInput i -> throw new IllegalStateException("reviewer broke") } as LearningReviewer

        when:
        trigger(reviewer).onAgentEnded(AgentEndedEvent.of("assistant", SESSION,
                AssistantMessage.builder().id("x").content("done").build()))
        Thread.sleep(200)

        then: "the user's turn already completed; a failed review is a non-event"
        noExceptionThrown()
        service.list("acme").isEmpty()
    }
}
