package io.jaiclaw.hermes.soul.markdown

import spock.lang.Specification

class MarkdownSectionEditorSpec extends Specification {

    def "add creates a new section when the heading is not present"() {
        when:
        String result = MarkdownSectionEditor.add("", "Identity", "I am helpful.")

        then:
        result.contains("# Identity")
        result.contains("I am helpful.")
    }

    def "add replaces in place when the heading already exists"() {
        given:
        String existing = "# Identity\nold body\n\n# Style\nconcise\n"

        when:
        String result = MarkdownSectionEditor.add(existing, "Identity", "new body")

        then:
        result.contains("new body")
        !result.contains("old body")
        result.contains("# Style")
        result.contains("concise")
    }

    def "add preserves section order"() {
        given:
        String existing = "# Identity\na\n\n# Style\nb\n"

        when:
        String result = MarkdownSectionEditor.add(existing, "Avoid", "c")

        then:
        result.indexOf("# Identity") < result.indexOf("# Style")
        result.indexOf("# Style") < result.indexOf("# Avoid")
    }

    def "replace errors when the heading does not exist"() {
        when:
        MarkdownSectionEditor.replace("# Identity\na\n", "Style", "b")

        then:
        thrown(MarkdownSectionEditor.UnknownSectionException)
    }

    def "replace updates the existing body"() {
        when:
        String result = MarkdownSectionEditor.replace("# Identity\nold\n", "Identity", "new")

        then:
        result.contains("new")
        !result.contains("old")
    }

    def "remove drops the section and keeps others"() {
        given:
        String existing = "# Identity\na\n\n# Style\nb\n\n# Avoid\nc\n"

        when:
        String result = MarkdownSectionEditor.remove(existing, "Style")

        then:
        result.contains("# Identity")
        result.contains("# Avoid")
        !result.contains("# Style")
        !result.contains(" b\n")
    }

    def "remove is idempotent on a missing heading"() {
        given:
        String existing = "# Identity\na\n"

        when:
        String result = MarkdownSectionEditor.remove(existing, "Style")

        then:
        result.contains("# Identity")
        result.contains("a")
        !result.contains("# Style")
    }

    def "## headings (H2) are NOT treated as section boundaries"() {
        given:
        String existing = "# Identity\nintro\n## subhead\ndetail\n"

        when:
        List<MarkdownSectionEditor.Section> sections = MarkdownSectionEditor.parse(existing)

        then:
        sections.size() == 1
        sections[0].heading() == "Identity"
        sections[0].body().contains("## subhead")
        sections[0].body().contains("detail")
    }

    def "round-trip parse + render preserves content"() {
        given:
        String original = "# Identity\nHelpful.\n\n# Style\nConcise.\n"

        when:
        String roundtripped = MarkdownSectionEditor.render(MarkdownSectionEditor.parse(original))

        then:
        roundtripped.contains("# Identity")
        roundtripped.contains("Helpful.")
        roundtripped.contains("# Style")
        roundtripped.contains("Concise.")
    }
}
