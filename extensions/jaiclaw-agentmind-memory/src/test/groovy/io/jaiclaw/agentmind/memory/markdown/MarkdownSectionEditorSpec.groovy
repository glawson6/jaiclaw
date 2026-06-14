package io.jaiclaw.agentmind.memory.markdown

import spock.lang.Specification

/**
 * Mirror of the soul module's MarkdownSectionEditorSpec, because the editor
 * is duplicated by value rather than by dependency (see
 * MarkdownSectionEditor's class-level Javadoc).
 */
class MarkdownSectionEditorSpec extends Specification {

    def "add creates a new section when the heading is not present"() {
        when:
        String result = MarkdownSectionEditor.add("", "Outages", "Use Slack #incidents.")

        then:
        result.contains("# Outages")
        result.contains("Use Slack #incidents.")
    }

    def "add replaces in place when the heading already exists"() {
        given:
        String existing = "# Outages\nold body\n\n# Escalation\nfollow runbook\n"

        when:
        String result = MarkdownSectionEditor.add(existing, "Outages", "new body")

        then:
        result.contains("new body")
        !result.contains("old body")
        result.contains("# Escalation")
    }

    def "replace errors when the heading does not exist"() {
        when:
        MarkdownSectionEditor.replace("# Outages\nslack\n", "Escalation", "runbook")

        then:
        thrown(MarkdownSectionEditor.UnknownSectionException)
    }

    def "remove drops the section and keeps others"() {
        given:
        String existing = "# A\n1\n\n# B\n2\n\n# C\n3\n"

        when:
        String result = MarkdownSectionEditor.remove(existing, "B")

        then:
        result.contains("# A")
        result.contains("# C")
        !result.contains("# B")
    }

    def "remove is idempotent on a missing heading"() {
        given:
        String existing = "# A\n1\n"

        when:
        String result = MarkdownSectionEditor.remove(existing, "B")

        then:
        result.contains("# A")
        !result.contains("# B")
    }

    def "## headings (H2) are NOT treated as section boundaries"() {
        given:
        String existing = "# Outages\nintro\n## subhead\ndetail\n"

        when:
        List<MarkdownSectionEditor.Section> sections = MarkdownSectionEditor.parse(existing)

        then:
        sections.size() == 1
        sections[0].heading() == "Outages"
        sections[0].body().contains("## subhead")
    }
}
