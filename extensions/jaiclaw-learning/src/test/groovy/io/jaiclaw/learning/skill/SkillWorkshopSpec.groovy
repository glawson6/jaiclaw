package io.jaiclaw.learning.skill

import io.jaiclaw.learning.LearningProperties
import io.jaiclaw.learning.apply.SkillProposalApplier
import io.jaiclaw.learning.curator.SkillCurator
import io.jaiclaw.learning.ledger.LearningLedger
import io.jaiclaw.learning.proposal.JsonFileProposalStore
import io.jaiclaw.learning.proposal.ProposalService
import io.jaiclaw.learning.proposal.ProposalState
import io.jaiclaw.learning.proposal.SkillPatchProposal
import io.jaiclaw.learning.proposal.SkillProposal
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SkillWorkshopSpec extends Specification {

    @TempDir
    Path tmp

    static final String TENANT = "acme"
    static final String SESSION = "assistant:slack:acme:C1"

    SkillWriter writer
    LearningLedger ledger
    ProposalService proposals
    SkillProposalApplier applier
    LearningProperties props

    def setup() {
        writer = new SkillWriter(tmp.resolve("skills"))
        ledger = new LearningLedger(tmp.resolve("ledger"))
        proposals = new ProposalService(new JsonFileProposalStore(tmp.resolve("proposals")))
        props = mode("propose", false)
        applier = new SkillProposalApplier(writer, ledger, proposals, props, null)
    }

    private static LearningProperties mode(String m, boolean allowPatches) {
        new LearningProperties(m, "balanced", 2, Duration.ZERO, 12000, "/tmp/p", "/tmp/s",
                allowPatches, true, Duration.ofDays(30), Duration.ofDays(90))
    }

    private SkillProposal newSkill(String name = "refund-flow",
                                   String body = "1. Look up the order\n2. Check the window") {
        proposals.submit(new SkillProposal(null, TENANT, SESSION, Instant.now(),
                ProposalState.PENDING, "learned refunds", name, "Handles refunds", body)).get()
    }

    private SkillPatchProposal patch(String name, String find, String replace) {
        proposals.submit(new SkillPatchProposal(null, TENANT, SESSION, Instant.now(),
                ProposalState.PENDING, "tighten", name, find, replace)).get()
    }

    // ── Create ───────────────────────────────────────────────────────────────

    def "applying a skill proposal writes SKILL.md and a sidecar under the tenant"() {
        given:
        def p = newSkill()

        when:
        def result = applier.applyIfEligible(p, "operator")

        then:
        result.applied()
        Files.exists(tmp.resolve("skills/acme/refund-flow/SKILL.md"))
        Files.exists(tmp.resolve("skills/acme/refund-flow/.jaiclaw-learning.json"))
        proposals.find(TENANT, p.id()).get().state() == ProposalState.APPLIED
    }

    def "the written SKILL.md carries parseable frontmatter marked as agent-authored"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")

        when:
        def raw = Files.readString(tmp.resolve("skills/acme/refund-flow/SKILL.md"))

        then: "an operator reading the file can tell an agent wrote it"
        raw.startsWith("---")
        raw.contains("name: refund-flow")
        raw.contains("description: Handles refunds")
        raw.contains("x-jaiclaw-learned: true")
        raw.contains("tenantIds: acme")
        raw.contains("1. Look up the order")
    }

    def "creating over an existing skill is refused rather than silently overwriting"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")

        when: "a second proposal reuses the name"
        def second = proposals.submit(new SkillProposal(null, TENANT, SESSION, Instant.now(),
                ProposalState.PENDING, "dup", "refund-flow", "Other", "different body")).get()
        def result = applier.applyIfEligible(second, "operator")

        then: "overwriting would destroy the original with no patch record"
        !result.applied()
        result.message().contains("already exists")
        writer.readBody(TENANT, "refund-flow").get().contains("Look up the order")
    }

    def "a skill name from a model cannot escape the tenant directory"() {
        given:
        def p = proposals.submit(new SkillProposal(null, TENANT, SESSION, Instant.now(),
                ProposalState.PENDING, "sneaky", "../../../etc/passwd", "d", "body")).get()

        when:
        applier.applyIfEligible(p, "operator")

        then:
        writer.skillDir(TENANT, "../../../etc/passwd").normalize()
                .startsWith(tmp.resolve("skills").normalize())
    }

    // ── Patch ────────────────────────────────────────────────────────────────

    def "a patch matching a unique span is applied and bumps the version"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def p = patch("refund-flow", "Check the window", "Check the 30-day window")

        when:
        def result = applier.applyIfEligible(p, "operator")

        then:
        result.applied()
        writer.readBody(TENANT, "refund-flow").get().contains("Check the 30-day window")
        writer.readSidecar(TENANT, "refund-flow").get().version() == 2
    }

    def "an ambiguous patch span is refused rather than guessed at"() {
        given: "a body where the target text appears twice"
        applier.applyIfEligible(newSkill("dup-skill", "step one\nstep one\ndone"), "operator")
        def p = patch("dup-skill", "step one", "step ONE")

        when:
        def result = applier.applyIfEligible(p, "operator")

        then: "a guessed edit would silently rewrite the wrong part of a live skill"
        !result.applied()
        result.message().contains("appears 2 times")
        writer.readBody(TENANT, "dup-skill").get().count("step one") == 2
    }

    def "a patch whose text is absent is refused"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")

        when:
        def result = applier.applyIfEligible(patch("refund-flow", "nonexistent text", "x"), "operator")

        then:
        !result.applied()
        result.message().contains("not found")
    }

    def "patching a skill that does not exist is refused"() {
        when:
        def result = applier.applyIfEligible(patch("no-such-skill", "a", "b"), "operator")

        then:
        !result.applied()
        result.message().contains("No learned skill")
    }

    def "auto mode does not auto-apply patches by default"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def autoApplier = new SkillProposalApplier(writer, ledger, proposals, mode("auto", false), null)

        when:
        def result = autoApplier.applyIfEligible(patch("refund-flow", "Check the window", "x"), "auto")

        then: "creating a skill is additive; editing one is destructive"
        !result.applied()
        result.message().contains("explicit apply")
    }

    def "auto patches are allowed when explicitly enabled"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def autoApplier = new SkillProposalApplier(writer, ledger, proposals, mode("auto", true), null)

        when:
        def result = autoApplier.applyIfEligible(
                patch("refund-flow", "Check the window", "Check the 30-day window"), "auto")

        then:
        result.applied()
    }

    // ── Rollback ─────────────────────────────────────────────────────────────

    def "rolling back a patch restores the previous bytes exactly"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def before = writer.readRaw(TENANT, "refund-flow").get()
        def p = patch("refund-flow", "Check the window", "Check the 30-day window")
        applier.applyIfEligible(p, "operator")

        when:
        def result = applier.rollback(p, "operator")

        then:
        result.applied()
        writer.readRaw(TENANT, "refund-flow").get() == before
        proposals.find(TENANT, p.id()).get().state() == ProposalState.ROLLED_BACK
    }

    def "rolling back a creation removes the skill"() {
        given:
        def p = newSkill()
        applier.applyIfEligible(p, "operator")

        when:
        def result = applier.rollback(p, "operator")

        then:
        result.applied()
        !writer.exists(TENANT, "refund-flow")
    }

    def "rollback fails closed when the ledger blob is missing"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def p = patch("refund-flow", "Check the window", "Check the 30-day window")
        applier.applyIfEligible(p, "operator")

        and: "the blob holding the prior version is lost"
        def blobRoot = tmp.resolve("ledger/acme/ledger/blobs")
        Files.walk(blobRoot).filter { Files.isRegularFile(it) }.forEach { Files.delete(it) }

        when:
        def result = applier.rollback(p, "operator")

        then: "a partial restore would be worse than none"
        !result.applied()
        result.message().contains("missing from the ledger")
    }

    def "rollback of an unknown proposal is refused"() {
        given:
        def p = new SkillProposal("never-applied", TENANT, SESSION, Instant.now(),
                ProposalState.PENDING, "x", "ghost", "d", "b")

        expect:
        !applier.rollback(p, "operator").applied()
    }

    def "the ledger records every mutation append-only"() {
        given:
        def create = newSkill()
        applier.applyIfEligible(create, "operator")
        def p = patch("refund-flow", "Check the window", "Check the 30-day window")
        applier.applyIfEligible(p, "operator")
        applier.rollback(p, "operator")

        when:
        def entries = ledger.entriesForSkill(TENANT, "refund-flow")

        then: "a rollback is recorded as a new entry, not by removing the one it reverses"
        entries*.action() == ["create", "patch", "rollback"]
        entries[0].isCreate()
        !entries[1].isCreate()
    }

    def "identical blob content is stored once"() {
        given:
        def blobs = ledger.blobs(TENANT)

        when:
        def h1 = blobs.put("same content")
        def h2 = blobs.put("same content")

        then:
        h1 == h2
        blobs.get(h1).get() == "same content"
    }

    // ── Curator ──────────────────────────────────────────────────────────────

    private SkillCurator curatorAt(Instant now) {
        new SkillCurator(writer, props, Clock.fixed(now, ZoneOffset.UTC))
    }

    def "an unused skill goes ACTIVE then STALE then ARCHIVED"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def created = writer.readSidecar(TENANT, "refund-flow").get().createdAt()

        expect: "still fresh"
        !curatorAt(created.plus(Duration.ofDays(1))).curate(TENANT).changedAnything()

        when: "31 days idle"
        def stale = curatorAt(created.plus(Duration.ofDays(31))).curate(TENANT)

        then:
        stale.madeStale() == ["refund-flow"]
        writer.readSidecar(TENANT, "refund-flow").get().lifecycle() == SkillLifecycle.STALE

        when: "91 days idle"
        def archived = curatorAt(created.plus(Duration.ofDays(91))).curate(TENANT)

        then:
        archived.archived() == ["refund-flow"]
        writer.readSidecar(TENANT, "refund-flow").get().lifecycle() == SkillLifecycle.ARCHIVED
    }

    def "an archived skill is retained on disk, not deleted"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def created = writer.readSidecar(TENANT, "refund-flow").get().createdAt()
        curatorAt(created.plus(Duration.ofDays(31))).curate(TENANT)
        curatorAt(created.plus(Duration.ofDays(91))).curate(TENANT)

        expect: "a record of what the agent learned survives"
        Files.exists(tmp.resolve("skills/acme/refund-flow/SKILL.md"))
        !writer.readSidecar(TENANT, "refund-flow").get().lifecycle().isLoadable()
    }

    def "a pinned skill never ages"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def sidecar = writer.readSidecar(TENANT, "refund-flow").get()
        writer.writeSidecar(TENANT, "refund-flow", sidecar.withPinned(true))

        when: "a year passes"
        def report = curatorAt(sidecar.createdAt().plus(Duration.ofDays(365))).curate(TENANT)

        then: "an operator's explicit keep outranks any usage heuristic"
        !report.changedAnything()
        writer.readSidecar(TENANT, "refund-flow").get().lifecycle() == SkillLifecycle.ACTIVE
    }

    def "recent use resets the ageing clock"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def sidecar = writer.readSidecar(TENANT, "refund-flow").get()
        def now = sidecar.createdAt().plus(Duration.ofDays(60))
        writer.writeSidecar(TENANT, "refund-flow", sidecar.withUse(now.minus(Duration.ofDays(1))))

        expect:
        !curatorAt(now).curate(TENANT).changedAnything()
    }

    def "a disabled curator does nothing"() {
        given:
        applier.applyIfEligible(newSkill(), "operator")
        def disabled = new LearningProperties("propose", "balanced", 2, Duration.ZERO, 12000, "/t", "/s",
                false, false, Duration.ofDays(30), Duration.ofDays(90))
        def curator = new SkillCurator(writer, disabled,
                Clock.fixed(Instant.now().plus(Duration.ofDays(365)), ZoneOffset.UTC))

        expect:
        !curator.curate(TENANT).changedAnything()
    }

    // ── Frontmatter helpers ──────────────────────────────────────────────────

    def "stripFrontmatter removes only the leading block"() {
        expect:
        SkillWriter.stripFrontmatter("---\nname: x\n---\n\nbody here") == "body here"
        SkillWriter.stripFrontmatter("no frontmatter") == "no frontmatter"
        SkillWriter.stripFrontmatter(null) == ""
    }

    def "listSkills returns learned skills for the tenant only"() {
        given:
        applier.applyIfEligible(newSkill("alpha"), "operator")
        applier.applyIfEligible(newSkill("beta"), "operator")

        expect:
        writer.listSkills(TENANT) == ["alpha", "beta"]
        writer.listSkills("other-tenant").isEmpty()
    }
}
