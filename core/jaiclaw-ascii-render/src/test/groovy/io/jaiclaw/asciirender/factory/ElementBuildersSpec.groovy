package io.jaiclaw.asciirender.factory

import io.jaiclaw.asciirender.api.IElement
import io.jaiclaw.asciirender.element.Circle
import io.jaiclaw.asciirender.element.Dot
import io.jaiclaw.asciirender.element.Ellipse
import io.jaiclaw.asciirender.element.Label
import io.jaiclaw.asciirender.element.Line
import io.jaiclaw.asciirender.element.Rectangle
import io.jaiclaw.asciirender.element.Table
import io.jaiclaw.asciirender.element.Text
import io.jaiclaw.asciirender.element.plot.Plot
import spock.lang.Specification
import spock.lang.Unroll

class ElementBuildersSpec extends Specification {

    @Unroll
    def "empty params yields no-arg constructor for #type"() {
        when:
        IElement element = ElementBuilders.dispatch(type, [:] as Map<String, Object>)

        then:
        klass.isInstance(element)

        where:
        type        | klass
        "rectangle" | Rectangle
        "ellipse"   | Ellipse
    }

    @Unroll
    def "any positional key flips to full constructor for #type"() {
        when:
        IElement element = ElementBuilders.dispatch(type, params)

        then:
        klass.isInstance(element)

        where:
        type        | klass     | params
        "rectangle" | Rectangle | [x: 2, y: 3, width: 10, height: 5]
        "ellipse"   | Ellipse   | [x: 2, y: 3, width: 10, height: 5]
        "text"      | Text      | [text: "hi", x: 2, y: 3, width: 10, height: 5]
    }

    def "label without positional keys yields default Label"() {
        when:
        IElement element = ElementBuilders.dispatch("label", [text: "hi"])

        then:
        element instanceof Label
    }

    def "label with x and y yields the x/y constructor"() {
        when:
        IElement element = ElementBuilders.dispatch("label", [text: "hi", x: 5, y: 7])

        then:
        element instanceof Label
    }

    def "label requires text"() {
        when:
        ElementBuilders.dispatch("label", [:])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("text")
    }

    def "line uses default origin pen when none provided"() {
        when:
        IElement element = ElementBuilders.dispatch("line", [x1: 0, y1: 0, x2: 5, y2: 5])

        then:
        element instanceof Line
    }

    def "line with pen uses the pen constructor"() {
        when:
        IElement element = ElementBuilders.dispatch("line", [x1: 0, y1: 0, x2: 3, y2: 3, pen: "*"])

        then:
        element instanceof Line
    }

    def "dot defaults to no-arg constructor when x and y absent"() {
        expect:
        ElementBuilders.dispatch("dot", [:]) instanceof Dot
    }

    def "circle with all three positional keys uses full constructor"() {
        expect:
        ElementBuilders.dispatch("circle", [x: 10, y: 10, radius: 4]) instanceof Circle
    }

    def "table requires positive rows and columns"() {
        when:
        ElementBuilders.dispatch("table", [rows: 0, columns: 3])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("positive")
    }

    def "table with explicit geometry uses the geometry constructor"() {
        when:
        IElement element = ElementBuilders.dispatch("table",
                [rows: 2, columns: 3, x: 0, y: 0, width: 20, height: 8])

        then:
        element instanceof Table
    }

    def "plot rejects missing points"() {
        when:
        ElementBuilders.dispatch("plot", [x: 0, y: 0, width: 40, height: 10])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("points")
    }

    def "plot rejects malformed point entries"() {
        when:
        ElementBuilders.dispatch("plot",
                [x: 0, y: 0, width: 40, height: 10, points: [[1.0]]])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("[x, y]")
    }

    def "plot accepts valid [x,y] pairs"() {
        when:
        IElement element = ElementBuilders.dispatch("plot",
                [x: 0, y: 0, width: 40, height: 10, points: [[1.0, 2.0], [3.0, 4.0]]])

        then:
        element instanceof Plot
    }

    def "intArg coerces string integers"() {
        when:
        IElement element = ElementBuilders.dispatch("rectangle",
                [x: "2", y: "3", width: "10", height: "5"])

        then:
        element instanceof Rectangle
    }

    def "intArg rejects garbage strings with a clear message"() {
        when:
        ElementBuilders.dispatch("rectangle", [x: "wat"])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("must be an integer")
    }

    def "unknown type returns null"() {
        expect:
        ElementBuilders.dispatch("marshmallow", [:]) == null
    }
}
