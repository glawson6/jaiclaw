package io.jaiclaw.learning.proposal

import io.jaiclaw.core.model.MemoryScope
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class ProposalStoreAndServiceSpec extends Specification {

    @TempDir
    Path tmp

    JsonFileProposalStore store
    ProposalService service

    def setup() {
        store = new JsonFileProposalStore(tmp)
        service = new ProposalService(store)
    }

    private static MemoryProposal memory(String tenant = "acme", String content = "prefers metric units",
                                         String id = null) {
        new MemoryProposal(id, tenant, "agent:slack:acme:C1", Instant.now(),
                ProposalState.PENDING, "remember unit preference",
                MemoryScope.AGENT, "Preferences", content)
    }

    private static SkillProposal skill(String tenant = "acme", String name = "refund-flow") {
        new SkillProposal(null, tenant, "agent:slack:acme:C1", Instant.now(),
                ProposalState.PENDING, "learned the refund workflow",
                name, "Handles refund requests", "1. Look up the order\n2. Check eligibility")
    }

    // ── Store ────────────────────────────────────────────────────────────────

    def "a saved proposal round-trips through JSON with its type intact"() {
        given:
        def p = service.submit(memory()).get()

        when:
        def loaded = store.find("acme", p.id())

        then:
        loaded.isPresent()
        loaded.get() instanceof MemoryProposal
        loaded.get().content() == "prefers metric units"
        loaded.get().scope() == MemoryScope.AGENT
        loaded.get().contentHash() == p.contentHash()
    }

    def "all three proposal kinds round-trip"() {
        given:
        def m = service.submit(memory()).get()
        def s = service.submit(skill()).get()
        def patch = service.submit(new SkillPatchProposal(null, "acme", "sess", Instant.now(),
                ProposalState.PENDING, "tighten step 2", "refund-flow",
                "Check eligibility", "Check eligibility against the 30-day window")).get()

        expect:
        store.find("acme", m.id()).get() instanceof MemoryProposal
        store.find("acme", s.id()).get() instanceof SkillProposal
        store.find("acme", patch.id()).get() instanceof SkillPatchProposal
    }

    def "writes are atomic — no .tmp file survives a successful save"() {
        when:
        service.submit(memory())

        then:
        Files.list(store.proposalsDir("acme")).noneMatch { it.toString().endsWith(".tmp") }
    }

    def "tenants are isolated even for identical content"() {
        when:
        def a = service.submit(memory("acme")).get()
        def b = service.submit(memory("globex")).get()

        then: "the tenant is part of the hash, so one tenant's dedupe can never suppress another's"
        a.contentHash() != b.contentHash()

        and: "two separate files, neither visible to the other tenant"
        store.list("acme").size() == 1
        store.list("globex").size() == 1
        store.find("globex", a.id()).isEmpty()
    }

    def "a corrupt file is quarantined and skipped rather than failing the queue"() {
        given:
        def good = service.submit(memory("acme", "keep me")).get()
        Files.writeString(store.proposalsDir("acme").resolve("broken.json"), "}{ not json")

        when:
        def listed = store.list("acme")

        then: "one damaged suggestion must not stop the agent"
        listed.size() == 1
        listed.first().id() == good.id()

        and: "and it is moved aside so it is not re-read every time"
        Files.list(store.proposalsDir("acme")).anyMatch { it.toString().contains(".corrupt-") }
    }

    def "a hostile tenant id cannot escape the base directory"() {
        when:
        service.submit(memory("../../etc", "sneaky"))

        then:
        !Files.exists(tmp.resolve("../../etc"))
        store.proposalsDir("../../etc").normalize().startsWith(tmp.normalize())
    }

    def "listing is newest-first"() {
        given:
        def older = new MemoryProposal(null, "acme", "s", Instant.parse("2026-01-01T00:00:00Z"),
                ProposalState.PENDING, "older", MemoryScope.AGENT, "H", "old content")
        def newer = new MemoryProposal(null, "acme", "s", Instant.parse("2026-06-01T00:00:00Z"),
                ProposalState.PENDING, "newer", MemoryScope.AGENT, "H", "new content")
        service.submit(older)
        service.submit(newer)

        expect:
        store.list("acme")*.summary() == ["newer", "older"]
    }

    // ── Dedupe ───────────────────────────────────────────────────────────────

    def "an identical proposal is dropped rather than queued twice"() {
        given:
        service.submit(memory())

        expect:
        service.submit(memory()).isEmpty()
        store.list("acme").size() == 1
    }

    def "a rejected proposal does not block the same suggestion later"() {
        given: "an operator rejects a suggestion"
        def first = service.submit(memory()).get()
        service.reject("acme", first.id(), "not now", "operator")

        expect: "the situation may change — the reviewer may offer it again"
        service.submit(memory()).isPresent()
    }

    // ── State machine ────────────────────────────────────────────────────────

    def "the happy path is PENDING -> APPLIED -> ROLLED_BACK"() {
        given:
        def p = service.submit(memory()).get()

        expect:
        service.markApplied("acme", p.id(), "operator").get().state() == ProposalState.APPLIED
        service.markRolledBack("acme", p.id(), "operator").get().state() == ProposalState.ROLLED_BACK
    }

    def "illegal transitions are refused"() {
        given:
        def p = service.submit(memory()).get()
        service.reject("acme", p.id(), "no", "operator")

        expect: "re-deciding an operator's rejection is not this module's job"
        service.markApplied("acme", p.id(), "system").isEmpty()
        store.find("acme", p.id()).get().state() == ProposalState.REJECTED
    }

    def "rollback requires a prior apply"() {
        given:
        def p = service.submit(memory()).get()

        expect:
        service.markRolledBack("acme", p.id(), "operator").isEmpty()
    }

    def "transitions on an unknown proposal return empty rather than throwing"() {
        expect:
        service.markApplied("acme", "no-such-id", "operator").isEmpty()
        service.reject("acme", "no-such-id", "x", "operator").isEmpty()
    }

    def "state transitions are enforced by the enum itself"() {
        expect:
        from.canTransitionTo(to) == allowed

        where:
        from                       | to                          | allowed
        ProposalState.PENDING      | ProposalState.APPLIED       | true
        ProposalState.PENDING      | ProposalState.REJECTED      | true
        ProposalState.PENDING      | ProposalState.ROLLED_BACK   | false
        ProposalState.APPLIED      | ProposalState.ROLLED_BACK   | true
        ProposalState.APPLIED      | ProposalState.REJECTED      | false
        ProposalState.REJECTED     | ProposalState.APPLIED       | false
        ProposalState.ROLLED_BACK  | ProposalState.APPLIED       | false
        ProposalState.PENDING      | ProposalState.PENDING       | false
    }

    def "listing filters by state"() {
        given:
        def a = service.submit(memory("acme", "one")).get()
        service.submit(memory("acme", "two"))
        service.markApplied("acme", a.id(), "operator")

        expect:
        service.list("acme", ProposalState.PENDING).size() == 1
        service.list("acme", ProposalState.APPLIED).size() == 1
    }

    def "content hashes distinguish semantically different proposals"() {
        expect:
        memory("acme", "alpha").contentHash() != memory("acme", "beta").contentHash()

        and: "and are not confused by field-boundary ambiguity"
        Proposal.hashOf("ab", "c") != Proposal.hashOf("a", "bc")
    }

    def "an id is assigned on submit when absent, and preserved when supplied"() {
        expect:
        service.submit(memory("acme", "auto")).get().id() != null
        service.submit(memory("acme", "explicit", "my-id")).get().id() == "my-id"
    }
}
