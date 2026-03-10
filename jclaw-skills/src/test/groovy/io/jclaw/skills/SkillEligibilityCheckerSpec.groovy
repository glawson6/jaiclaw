package io.jclaw.skills

import io.jclaw.core.skill.SkillDefinition
import io.jclaw.core.skill.SkillMetadata
import spock.lang.Specification

class SkillEligibilityCheckerSpec extends Specification {

    def "skill with no constraints is always eligible"() {
        given:
        def checker = new SkillEligibilityChecker("darwin")
        def skill = new SkillDefinition("any", "desc", "content", SkillMetadata.EMPTY)

        expect:
        checker.isEligible(skill)
    }

    def "skill is eligible on matching platform"() {
        given:
        def checker = new SkillEligibilityChecker("darwin")
        def meta = new SkillMetadata(false, "", Set.of(), Set.of("darwin", "linux"))
        def skill = new SkillDefinition("mac-skill", "desc", "content", meta)

        expect:
        checker.isEligible(skill)
    }

    def "skill is not eligible on non-matching platform"() {
        given:
        def checker = new SkillEligibilityChecker("windows")
        def meta = new SkillMetadata(false, "", Set.of(), Set.of("darwin", "linux"))
        def skill = new SkillDefinition("unix-only", "desc", "content", meta)

        expect:
        !checker.isEligible(skill)
    }

    def "skill with required bin 'sh' is eligible (sh always exists)"() {
        given:
        def checker = new SkillEligibilityChecker("darwin")
        def meta = new SkillMetadata(false, "", Set.of("sh"), Set.of())
        def skill = new SkillDefinition("sh-skill", "desc", "content", meta)

        expect:
        checker.isEligible(skill)
    }

    def "skill with nonexistent required bin is not eligible"() {
        given:
        def checker = new SkillEligibilityChecker("darwin")
        def meta = new SkillMetadata(false, "", Set.of("definitely_not_installed_xyz_12345"), Set.of())
        def skill = new SkillDefinition("missing-bin", "desc", "content", meta)

        expect:
        !checker.isEligible(skill)
    }
}
