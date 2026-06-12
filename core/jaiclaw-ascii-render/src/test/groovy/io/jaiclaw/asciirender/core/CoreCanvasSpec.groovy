package io.jaiclaw.asciirender.core

import io.jaiclaw.asciirender.api.ICanvas
import io.jaiclaw.asciirender.api.IRender
import spock.lang.Specification

/**
 * Smoke-tests the canvas + empty-render path. Element-specific specs
 * live alongside their element classes.
 */
class CoreCanvasSpec extends Specification {

    def "an empty canvas renders as a grid of spaces with one newline per row"() {
        when:
        ICanvas c = new Canvas(4, 2)

        then:
        c.text == "    \n    "
    }

    def "draw + getText returns the placed characters with nulls filled in as spaces"() {
        given:
        Canvas c = new Canvas(5, 1)

        when:
        c.draw(1, 0, "hi")

        then:
        c.text == " hi  "
    }

    def "draw clips characters past the right edge"() {
        given:
        Canvas c = new Canvas(5, 1)

        when:
        c.draw(3, 0, "hello")

        then:
        c.text == "   he"
    }

    def "draw with negative x clips characters that fall off the left edge"() {
        given:
        Canvas c = new Canvas(5, 1)

        when:
        c.draw(-2, 0, "hello")

        then:
        c.text == "llo  "
    }

    def "multiline draw splits on the first newline"() {
        given:
        Canvas c = new Canvas(4, 2)

        when:
        c.draw(0, 0, "ab\ncd")

        then:
        c.text == "ab  \ncd  "
    }

    def "Point and Region records satisfy IPoint and IRegion accessors"() {
        when:
        Point p = new Point(3, 4)
        Region r = new Region(1, 2, 5, 6)

        then:
        p.getX() == 3
        p.getY() == 4
        r.getX() == 1
        r.getY() == 2
        r.getWidth() == 5
        r.getHeight() == 6
    }

    def "Region rejects negative width or height"() {
        when:
        new Region(0, 0, w, h)

        then:
        thrown(IllegalArgumentException)

        where:
        w  | h
        -1 | 0
        0  | -1
    }

    def "trim removes both spaces and null cells around the bounding box"() {
        given:
        // A fresh canvas is filled with \0; trim() handles both \0 and ' '
        // (trimSpaces would leave the nulls because they aren't spaces).
        Canvas c = new Canvas(5, 3)
        c.draw(1, 1, "X")

        when:
        ICanvas trimmed = c.trim()

        then:
        trimmed.text == "X"
        trimmed.width == 1
        trimmed.height == 1
    }

    def "Render with one empty layer produces an empty canvas"() {
        given:
        IRender render = new Render()

        when:
        ICanvas c = render.render(render.newBuilder()
                .width(3).height(2)
                .layer()
                .build())

        then:
        c.text == "   \n   "
    }
}
