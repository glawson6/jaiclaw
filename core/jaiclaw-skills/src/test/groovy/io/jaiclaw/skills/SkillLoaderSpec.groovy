package io.jaiclaw.skills

import io.jaiclaw.core.skill.SkillDefinition
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

    def "loadBundled loads bundled skills (filtered by binary availability)"() {
        when:
        def skills = loader.loadBundled()

        then:
        // At least the skills with no requiredBins should always load
        skills.size() >= 7
        def names = skills.collect { it.name() } as Set
        // Core skills with no requiredBins or only curl (universally available) should always load
        names.containsAll(["coding", "web-research", "system-admin", "conversation", "summarize", "jaiclaw-developer"])
        // Skills requiring specific binaries may be filtered out depending on the machine
        // Total bundled skills on classpath: 29 (7 original + 21 ported + cli-architect)
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

    // --- loadConfigured tests ---

    def "loadConfigured with default properties loads all bundled"() {
        when:
        def skills = loader.loadConfigured(["*"], null)

        then:
        skills.size() == loader.loadBundled().size()
    }

    def "loadConfigured with empty allowBundled loads no bundled skills"() {
        when:
        def skills = loader.loadConfigured([], null)

        then:
        skills.isEmpty()
    }

    def "loadConfigured with specific allowBundled filters bundled skills"() {
        when:
        def skills = loader.loadConfigured(["coding", "conversation"], null)

        then:
        skills.size() == 2
        skills.collect { it.name() } as Set == ["coding", "conversation"] as Set
    }

    def "loadConfigured with workspaceDir loads workspace skills"() {
        given:
        def dir = tempDir.resolve("ws-skill")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: ws-skill
description: A workspace skill
---
Workspace content
""".stripIndent())

        when:
        def skills = loader.loadConfigured([], tempDir.toString())

        then:
        skills.size() == 1
        skills[0].name() == "ws-skill"
    }

    def "loadConfigured with both loads bundled and workspace"() {
        given:
        def dir = tempDir.resolve("extra-skill")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: extra-skill
description: An extra skill
---
Extra content
""".stripIndent())

        when:
        def skills = loader.loadConfigured(["*"], tempDir.toString())

        then:
        skills.size() == loader.loadBundled().size() + 1
        skills.any { it.name() == "extra-skill" }
    }

    def "loadConfigured workspace skill overrides same-name bundled skill"() {
        given:
        def dir = tempDir.resolve("coding")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: coding
description: Custom coding skill
---
Custom coding content
""".stripIndent())

        when:
        def skills = loader.loadConfigured(["*"], tempDir.toString())

        then:
        def codingSkill = skills.find { it.name() == "coding" }
        codingSkill != null
        codingSkill.description() == "Custom coding skill"
        codingSkill.content().contains("Custom coding content")
        // Total should be bundled count (not bundled+1) since coding was overridden
        skills.size() == loader.loadBundled().size()
    }

    def "loadConfigured with empty allowBundled and workspaceDir loads only workspace"() {
        given:
        def dir = tempDir.resolve("only-skill")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("SKILL.md"), """\
---
name: only-skill
description: The only skill
---
Only content
""".stripIndent())

        when:
        def skills = loader.loadConfigured([], tempDir.toString())

        then:
        skills.size() == 1
        skills[0].name() == "only-skill"
    }

    def "loadConfigured with null workspaceDir skips workspace loading"() {
        when:
        def skills = loader.loadConfigured(["coding"], null)

        then:
        skills.size() == 1
        skills[0].name() == "coding"
    }
}
