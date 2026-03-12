package io.jclaw.skills

import io.jclaw.core.skill.SkillDefinition
import io.jclaw.core.skill.SkillMetadata
import spock.lang.Specification

class TenantSkillRegistrySpec extends Specification {

    def globalSkill = new SkillDefinition("coding", "Coding help", "# Code",
            new SkillMetadata(false, "", [] as Set, [] as Set, "1.0.0", [] as Set))
    def tenantSkill = new SkillDefinition("coaching", "Coaching skill", "# Coach",
            new SkillMetadata(false, "", [] as Set, [] as Set, "2.0.0", ["tenant-a"] as Set))
    def multiTenantSkill = new SkillDefinition("research", "Research", "# Research",
            new SkillMetadata(false, "", [] as Set, [] as Set, "1.1.0", ["tenant-a", "tenant-b"] as Set))

    def registry = new TenantSkillRegistry([globalSkill, tenantSkill, multiTenantSkill])

    def "null tenant gets all skills"() {
        expect:
        registry.getSkillsForTenant(null).size() == 3
    }

    def "tenant-a sees global + its own skills"() {
        when:
        def skills = registry.getSkillsForTenant("tenant-a")

        then:
        skills.size() == 3
        skills*.name().containsAll(["coding", "coaching", "research"])
    }

    def "tenant-b sees global + multi-tenant skill"() {
        when:
        def skills = registry.getSkillsForTenant("tenant-b")

        then:
        skills.size() == 2
        skills*.name().containsAll(["coding", "research"])
        !skills*.name().contains("coaching")
    }

    def "tenant-c only sees global skills"() {
        when:
        def skills = registry.getSkillsForTenant("tenant-c")

        then:
        skills.size() == 1
        skills[0].name() == "coding"
    }

    def "getSkill by name and tenant"() {
        expect:
        registry.getSkill("tenant-a", "coaching").isPresent()
        !registry.getSkill("tenant-c", "coaching").isPresent()
    }

    def "getSkill by name, tenant, and version"() {
        expect:
        registry.getSkill("tenant-a", "coaching", "2.0.0").isPresent()
        !registry.getSkill("tenant-a", "coaching", "1.0.0").isPresent()
    }

    def "size returns total skill count"() {
        expect:
        registry.size() == 3
    }

    def "invalidateCache clears cached tenant resolution"() {
        given:
        registry.getSkillsForTenant("tenant-a") // populate cache

        when:
        registry.invalidateCache("tenant-a")

        then: "no error — cache cleared"
        registry.getSkillsForTenant("tenant-a").size() == 3
    }

    def "invalidateAllCaches clears everything"() {
        given:
        registry.getSkillsForTenant("tenant-a")
        registry.getSkillsForTenant("tenant-b")

        when:
        registry.invalidateAllCaches()

        then: "no error"
        registry.getSkillsForTenant("tenant-a").size() == 3
    }
}
