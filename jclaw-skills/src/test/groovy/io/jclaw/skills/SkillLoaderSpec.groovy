package io.jclaw.skills

import io.jclaw.core.skill.SkillDefinition
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SkillLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    SkillLoader loader = new SkillLoader(new SkillEligibilityChecker("darwin"))

    def "loadFromDirectory finds SKILL.md files"() {
        given:
        def skillDir = tempDir.resolve("my-skill")
        Files.createDirectories(skillDir)
        Files.writeString(skillDir.resolve("SKILL.md"), """\
---
name: test-skill
description: A test skill
---
# Test Skill

This is a test.
""".stripIndent())

        when:
        def skills = loader.loadFromDirectory(tempDir)

        then:
        skills.size() == 1
        skills[0].name() == "test-skill"
        skills[0].description() == "A test skill"
        skills[0].content().contains("# Test Skill")
    }

    def "loadFromDirectory returns empty for nonexistent directory"() {
        expect:
        loader.loadFromDirectory(tempDir.resolve("nope")).isEmpty()
    }

    def "loadFromDirectory loads multiple skills"() {
        given:
        ["skill-a", "skill-b", "skill-c"].each { name ->
            def dir = tempDir.resolve(name)
            Files.createDirectories(dir)
            Files.writeString(dir.resolve("SKILL.md"), """\
---
name: ${name}
description: Skill ${name}
---
Content for ${name}
""".stripIndent())
        }

        when:
        def skills = loader.loadFromDirectory(tempDir)

        then:
        skills.size() == 3
        skills.collect { it.name() } as Set == ["skill-a", "skill-b", "skill-c"] as Set
    }

    def "loadFromDirectory filters ineligible skills by platform"() {
        given:
        def winLoader = new SkillLoader(new SkillEligibilityChecker("windows"))
        def dir = tempDir.resolve("mac-only")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: mac-tool
platforms: [darwin]
---
Mac only content
""".stripIndent())

        when:
        def skills = winLoader.loadFromDirectory(tempDir)

        then:
        skills.isEmpty()
    }

    def "loadAll combines bundled and directory skills"() {
        given:
        def dir = tempDir.resolve("custom")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: custom-skill
---
Custom content
""".stripIndent())

        when:
        def skills = loader.loadAll(tempDir)

        then:
        skills.size() >= 1
        skills.any { it.name() == "custom-skill" }
    }

    def "loadBundled loads all 6 bundled skills"() {
        when:
        def skills = loader.loadBundled()

        then:
        skills.size() == 7
        skills.collect { it.name() } as Set == ["coding", "web-research", "system-admin", "conversation", "summarize", "k8s-monitoring", "jclaw-developer"] as Set
    }

    def "bundled skills have correct alwaysInclude flags"() {
        when:
        def skills = loader.loadBundled()
        def alwaysIncluded = skills.findAll { it.metadata().alwaysInclude() }

        then:
        alwaysIncluded.collect { it.name() } as Set == ["coding", "conversation"] as Set
    }

    def "bundled skills all have descriptions"() {
        when:
        def skills = loader.loadBundled()

        then:
        skills.every { !it.description().isBlank() }
    }
}
