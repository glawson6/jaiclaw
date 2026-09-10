package io.jaiclaw.skills

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SkillLoaderLearnedSpec extends Specification {

    @TempDir
    Path tmp

    SkillLoader loader = new SkillLoader()

    private void writeLearnedSkill(String tenant, String name, String lifecycle = "ACTIVE") {
        def dir = tmp.resolve(tenant).resolve(name)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: $name
description: Learned skill $name
version: 1.0.0
x-jaiclaw-learned: true
---

1. do the thing
""")
        if (lifecycle != null) {
            Files.writeString(dir.resolve(".jaiclaw-learning.json"),
                    """{"skillName":"$name","tenantId":"$tenant","lifecycle":"$lifecycle","version":1}""")
        }
    }

    def "learned skills for a tenant are loaded"() {
        given:
        writeLearnedSkill("acme", "refund-flow")
        writeLearnedSkill("acme", "escalation")

        when:
        def skills = loader.loadLearned(tmp, "acme")

        then:
        skills*.name().toSorted() == ["escalation", "refund-flow"]
        skills.find { it.name() == "refund-flow" }.content().contains("do the thing")
    }

    def "ARCHIVED skills are skipped but left on disk"() {
        given:
        writeLearnedSkill("acme", "active-one", "ACTIVE")
        writeLearnedSkill("acme", "stale-one", "STALE")
        writeLearnedSkill("acme", "archived-one", "ARCHIVED")

        when:
        def skills = loader.loadLearned(tmp, "acme")

        then: "archiving takes effect at load time"
        skills*.name().toSorted() == ["active-one", "stale-one"]

        and: "but the record of what the agent learned survives"
        Files.exists(tmp.resolve("acme/archived-one/SKILL.md"))
    }

    def "STALE skills still load — staleness is a warning, not a removal"() {
        given:
        writeLearnedSkill("acme", "aging", "STALE")

        expect:
        loader.loadLearned(tmp, "acme")*.name() == ["aging"]
    }

    def "tenants are isolated"() {
        given:
        writeLearnedSkill("acme", "acme-only")
        writeLearnedSkill("globex", "globex-only")

        expect:
        loader.loadLearned(tmp, "acme")*.name() == ["acme-only"]
        loader.loadLearned(tmp, "globex")*.name() == ["globex-only"]
    }

    def "a missing sidecar is treated as active"() {
        given: "a skill written without a sidecar"
        writeLearnedSkill("acme", "no-sidecar", null)

        expect: "a skill on disk loads unless something positively says not to"
        loader.loadLearned(tmp, "acme")*.name() == ["no-sidecar"]
    }

    def "an unreadable sidecar fails open"() {
        given:
        writeLearnedSkill("acme", "broken-sidecar")
        Files.writeString(tmp.resolve("acme/broken-sidecar/.jaiclaw-learning.json"), "}{ not json")

        expect:
        loader.loadLearned(tmp, "acme")*.name() == ["broken-sidecar"]
    }

    def "lifecycle matching is case-insensitive"() {
        given:
        writeLearnedSkill("acme", "lower", "archived")

        expect:
        loader.loadLearned(tmp, "acme").isEmpty()
    }

    def "a missing directory or null input yields an empty list, not an error"() {
        when:
        def missingTenant = loader.loadLearned(tmp, "never-existed")
        def missingDir = loader.loadLearned(tmp.resolve("nope"), "acme")
        def nullBase = loader.loadLearned(null, "acme")

        then:
        noExceptionThrown()
        missingTenant.isEmpty()
        missingDir.isEmpty()
        nullBase.isEmpty()
    }

    def "a null tenant resolves to the default directory"() {
        given:
        writeLearnedSkill("default", "shared")

        expect:
        loader.loadLearned(tmp, null)*.name() == ["shared"]
    }

    def "a hostile tenant id cannot escape the learned directory"() {
        given:
        writeLearnedSkill("acme", "safe")

        when:
        def result = loader.loadLearned(tmp, "../../etc")

        then: "traversal is sanitised the same way the writer sanitises it"
        noExceptionThrown()
        result.isEmpty()
    }

    def "a directory without a SKILL.md is ignored"() {
        given:
        Files.createDirectories(tmp.resolve("acme/empty-dir"))
        writeLearnedSkill("acme", "real-one")

        expect:
        loader.loadLearned(tmp, "acme")*.name() == ["real-one"]
    }

    // ── Deferred invalidation ────────────────────────────────────────────────

    def "a skill applied mid-session is absent from that session's load and present in the next"() {
        given: "a session has already loaded its skills"
        writeLearnedSkill("acme", "existing")
        def sessionOneSkills = loader.loadLearned(tmp, "acme")

        when: "the learning module applies a new skill while that session is still running"
        writeLearnedSkill("acme", "newly-applied")

        then: "the already-loaded list is unchanged — the running session never sees it"
        sessionOneSkills*.name() == ["existing"]

        when: "the next session loads"
        def sessionTwoSkills = loader.loadLearned(tmp, "acme")

        then: "it picks the new skill up"
        sessionTwoSkills*.name().toSorted() == ["existing", "newly-applied"]
    }

    def "archiving mid-session likewise only takes effect on the next load"() {
        given:
        writeLearnedSkill("acme", "doomed")
        def before = loader.loadLearned(tmp, "acme")

        when: "the curator archives it"
        Files.writeString(tmp.resolve("acme/doomed/.jaiclaw-learning.json"),
                '{"skillName":"doomed","lifecycle":"ARCHIVED","version":1}')

        then:
        before*.name() == ["doomed"]
        loader.loadLearned(tmp, "acme").isEmpty()
    }
}
