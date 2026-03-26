package io.jaiclaw.skill.creator

import spock.lang.Specification

class SkillCreatorCommandsSpec extends Specification {

    def "extractSkillContent extracts from markdown fenced block"() {
        given:
        def response = '''\
Here is the skill:

```markdown
---
name: test-skill
description: A test skill
---

# Test Skill

Instructions here.
```

Hope that helps!'''

        when:
        def content = SkillCreatorCommands.extractSkillContent(response)

        then:
        content.startsWith('---')
        content.contains('name: test-skill')
        content.contains('# Test Skill')
        content.contains('Instructions here.')
        !content.contains('Hope that helps')
        !content.contains('```')
    }

    def "extractSkillContent extracts from generic fenced block"() {
        given:
        def response = '''\
```
---
name: test-skill
description: A test skill
---

# Test Skill
```'''

        when:
        def content = SkillCreatorCommands.extractSkillContent(response)

        then:
        content.startsWith('---')
        content.contains('name: test-skill')
    }

    def "extractSkillContent uses raw frontmatter when no fence"() {
        given:
        def response = '''\
---
name: test-skill
description: A test skill
---

# Test Skill

Content here.'''

        when:
        def content = SkillCreatorCommands.extractSkillContent(response)

        then:
        content.startsWith('---')
        content.contains('name: test-skill')
        content.contains('Content here.')
    }

    def "extractSkillContent returns stripped response as fallback"() {
        given:
        def response = '  Some raw text response  '

        when:
        def content = SkillCreatorCommands.extractSkillContent(response)

        then:
        content == 'Some raw text response'
    }

    def "extractSkillContent handles null and blank"() {
        expect:
        SkillCreatorCommands.extractSkillContent(null) == ''
        SkillCreatorCommands.extractSkillContent('') == ''
        SkillCreatorCommands.extractSkillContent('   ') == ''
    }

    def "extractSkillName parses name from frontmatter"() {
        given:
        def content = '''\
---
name: my-cool-skill
description: Does things
---

# My Cool Skill'''

        expect:
        SkillCreatorCommands.extractSkillName(content, 'fallback') == 'my-cool-skill'
    }

    def "extractSkillName removes quotes from name"() {
        given:
        def content = '''\
---
name: "quoted-skill"
description: Does things
---'''

        expect:
        SkillCreatorCommands.extractSkillName(content, 'fallback') == 'quoted-skill'
    }

    def "extractSkillName returns fallback when no frontmatter"() {
        expect:
        SkillCreatorCommands.extractSkillName('# No frontmatter', 'fallback') == 'fallback'
    }

    def "extractSkillName returns fallback when name not in frontmatter"() {
        given:
        def content = '''\
---
description: No name field
---'''

        expect:
        SkillCreatorCommands.extractSkillName(content, 'fallback') == 'fallback'
    }

    def "extractSkillName returns fallback for null content"() {
        expect:
        SkillCreatorCommands.extractSkillName(null, 'fallback') == 'fallback'
    }
}
