package io.jclaw.skills

import io.jclaw.core.skill.SkillMetadata
import spock.lang.Specification

class SkillVersioningSpec extends Specification {

    def "default version is 1.0.0"() {
        expect:
        SkillMetadata.EMPTY.version() == "1.0.0"
    }

    def "version preserved in constructor"() {
        when:
        def meta = new SkillMetadata(false, "", [] as Set, [] as Set, "3.2.1", [] as Set)

        then:
        meta.version() == "3.2.1"
    }

    def "null version defaults to 1.0.0"() {
        when:
        def meta = new SkillMetadata(false, "", [] as Set, [] as Set, null, [] as Set)

        then:
        meta.version() == "1.0.0"
    }

    def "isAvailableToTenant with empty tenantIds returns true for any tenant"() {
        given:
        def meta = new SkillMetadata(false, "", [] as Set, [] as Set, "1.0.0", [] as Set)

        expect:
        meta.isAvailableToTenant("any-tenant")
        meta.isAvailableToTenant("another")
    }

    def "isAvailableToTenant with specific tenantIds"() {
        given:
        def meta = new SkillMetadata(false, "", [] as Set, [] as Set, "1.0.0", ["t1", "t2"] as Set)

        expect:
        meta.isAvailableToTenant("t1")
        meta.isAvailableToTenant("t2")
        !meta.isAvailableToTenant("t3")
    }

    def "backward compatible 4-arg constructor defaults version and tenantIds"() {
        when:
        def meta = new SkillMetadata(true, "node", ["git"] as Set, ["darwin"] as Set)

        then:
        meta.version() == "1.0.0"
        meta.tenantIds() == [] as Set
        meta.alwaysInclude()
        meta.primaryEnv() == "node"
    }

    def "parser extracts version from frontmatter"() {
        given:
        def parser = new SkillMarkdownParser()
        def content = """---
name: test-skill
description: A test skill
version: 2.5.0
tenantIds: [acme, beta]
---
# Test content
"""

        when:
        def skill = parser.parse("test-SKILL.md", content)

        then:
        skill.name() == "test-skill"
        skill.metadata().version() == "2.5.0"
        skill.metadata().tenantIds().containsAll(["acme", "beta"])
    }
}
