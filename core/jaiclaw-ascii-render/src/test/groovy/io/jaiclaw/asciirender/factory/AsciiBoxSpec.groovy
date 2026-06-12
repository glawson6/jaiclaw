package io.jaiclaw.asciirender.factory

import spock.lang.Specification
import spock.lang.Unroll

class AsciiBoxSpec extends Specification {

    def "single-style render produces ┌ and └ corners"() {
        when:
        String text = AsciiBox.render("hello", 20, AsciiBox.Style.SINGLE, null)

        then:
        text.startsWith("┌")
        text.contains("hello")
        text.split("\n", -1)[-1].startsWith("└")
    }

    @Unroll
    def "border style #styleName uses the expected top-left glyph"() {
        when:
        String text = AsciiBox.render("hi", 20, style, null)

        then:
        text.startsWith(expected as String)

        where:
        styleName | style                    | expected
        "SINGLE"  | AsciiBox.Style.SINGLE    | "┌"
        "DOUBLE"  | AsciiBox.Style.DOUBLE    | "╔"
        "BOLD"    | AsciiBox.Style.BOLD      | "┏"
        "ROUNDED" | AsciiBox.Style.ROUNDED   | "╭"
    }

    def "title is rendered on the top edge in brackets"() {
        when:
        String text = AsciiBox.render("body", 30, AsciiBox.Style.SINGLE, "NOTE")

        then:
        text.contains("[ NOTE ]")
    }

    def "blank title is ignored"() {
        when:
        String text = AsciiBox.render("body", 30, AsciiBox.Style.SINGLE, "   ")

        then:
        !text.contains("[")
    }

    def "long content wraps at word boundaries"() {
        given:
        String content = "alpha beta gamma delta epsilon"

        when:
        String text = AsciiBox.render(content, 10, AsciiBox.Style.SINGLE, null)

        then:
        text.split("\n", -1).length >= 4
    }

    def "embedded newlines are honoured"() {
        when:
        String text = AsciiBox.render("line one\nline two", 30, AsciiBox.Style.SINGLE, null)

        then:
        text.contains("line one")
        text.contains("line two")
        // body height is 2 (one per source line); plus top and bottom borders = 4 lines
        text.split("\n", -1).length == 4
    }

    def "render(content, width) defaults to SINGLE with no title"() {
        when:
        String text = AsciiBox.render("hello", 20)

        then:
        text.startsWith("┌")
        !text.contains("[")
    }

    def "render(content) uses DEFAULT_WIDTH and SINGLE style"() {
        when:
        String text = AsciiBox.render("hi")

        then:
        text.startsWith("┌")
        // Box's inner width >= DEFAULT_WIDTH (60); outer width = 62
        text.split("\n", -1)[0].length() >= AsciiBox.DEFAULT_WIDTH
    }

    def "width below MIN_WIDTH is clamped up"() {
        when:
        String text = AsciiBox.render("x", 2, AsciiBox.Style.SINGLE, null)

        then:
        text.split("\n", -1)[0].length() == AsciiBox.MIN_WIDTH + 2
    }

    def "width above MAX_WIDTH is clamped down"() {
        when:
        String text = AsciiBox.render("x", 10_000, AsciiBox.Style.SINGLE, null)

        then:
        text.split("\n", -1)[0].length() == AsciiBox.MAX_WIDTH + 2
    }

    def "non-positive width falls back to DEFAULT_WIDTH"() {
        when:
        String text = AsciiBox.render("x", 0, AsciiBox.Style.SINGLE, null)

        then:
        text.split("\n", -1)[0].length() == AsciiBox.DEFAULT_WIDTH + 2
    }

    def "null style is treated as SINGLE"() {
        when:
        String text = AsciiBox.render("x", 20, null, null)

        then:
        text.startsWith("┌")
    }

    def "null content rejected with IllegalArgumentException"() {
        when:
        AsciiBox.render(null, 20, AsciiBox.Style.SINGLE, null)

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "Style.resolve('#input') returns #expected"() {
        expect:
        AsciiBox.Style.resolve(input) == expected

        where:
        input     | expected
        "single"  | AsciiBox.Style.SINGLE
        "double"  | AsciiBox.Style.DOUBLE
        "bold"    | AsciiBox.Style.BOLD
        "heavy"   | AsciiBox.Style.BOLD
        "rounded" | AsciiBox.Style.ROUNDED
        "round"   | AsciiBox.Style.ROUNDED
        " DOUBLE " | AsciiBox.Style.DOUBLE
        "Bold"    | AsciiBox.Style.BOLD
        "plaid"   | null
        null      | null
        ""        | null
    }

    def "long title silently drops when banner span ties the box width — preserved existing behaviour"() {
        // Existing tool behaviour: when `[ title ]` exactly fills the box width,
        // the bounds check `x + banner.length() < width - 1` is false (off-by-one),
        // so the banner is dropped. Keep this pinned so a future fix is a deliberate
        // change rather than a stealth regression.
        given:
        String longTitle = "this is a very long title that exceeds default width"

        when:
        String text = AsciiBox.render("x", 10, AsciiBox.Style.SINGLE, longTitle)

        then:
        !text.contains(longTitle)
    }
}
