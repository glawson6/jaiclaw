package io.jclaw.skills

import spock.lang.Specification

class SkillMarkdownParserSpec extends Specification {

    SkillMarkdownParser parser = new SkillMarkdownParser()

    def "parses frontmatter and content"() {
        given:
        def markdown = """\
---
name: git-commit
description: Helps with git operations
alwaysInclude: true
requiredBins: [git]
platforms: [darwin, linux]
---
# Git Commit Skill

Use this skill to commit changes.
""".stripIndent()

        when:
        def skill = parser.parse("SKILL.md", markdown)

        then:
        skill.name() == "git-commit"
        skill.description() == "Helps with git operations"
        skill.metadata().alwaysInclude()
        skill.metadata().requiredBins() == ["git"] as Set
        skill.metadata().supportedPlatforms() == ["darwin", "linux"] as Set
        skill.content().contains("# Git Commit Skill")
        skill.content().contains("Use this skill to commit changes.")
    }

    def "parses without frontmatter"() {
        given:
        def markdown = "# Simple Skill\n\nJust content, no frontmatter."

        when:
        def skill = parser.parse("simple-SKILL.md", markdown)

        then:
        skill.name() == "simple-"
        skill.content() == "# Simple Skill\n\nJust content, no frontmatter."
        !skill.metadata().alwaysInclude()
        skill.metadata().requiredBins().isEmpty()
    }

    def "derives name from filename when not in frontmatter"() {
        given:
        def markdown = """\
---
description: A cool skill
---
Content here
""".stripIndent()

        when:
        def skill = parser.parse("my-cool-SKILL.md", markdown)

        then:
        skill.name() == "my-cool-"
    }

    def "parses empty frontmatter gracefully"() {
        given:
        def markdown = """\
---
---
Just the content.
""".stripIndent()

        when:
        def skill = parser.parse("test-SKILL.md", markdown)

        then:
        skill.content() == "Just the content."
        !skill.metadata().alwaysInclude()
    }

    def "parses primaryEnv from frontmatter"() {
        given:
        def markdown = """\
---
name: node-skill
primaryEnv: node
---
Node skill content
""".stripIndent()

        when:
        def skill = parser.parse("SKILL.md", markdown)

        then:
        skill.metadata().primaryEnv() == "node"
    }

    def "handles missing optional fields"() {
        given:
        def markdown = """\
---
name: minimal
---
Minimal skill
""".stripIndent()

        when:
        def skill = parser.parse("SKILL.md", markdown)

        then:
        skill.name() == "minimal"
        skill.description() == ""
        !skill.metadata().alwaysInclude()
        skill.metadata().requiredBins().isEmpty()
        skill.metadata().supportedPlatforms().isEmpty()
        skill.metadata().primaryEnv() == ""
    }
}
