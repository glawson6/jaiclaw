package io.jaiclaw.asciirender.factory

import spock.lang.Specification

class SceneSpecExceptionSpec extends Specification {

    def "is a subclass of IllegalArgumentException (catch compatibility)"() {
        expect:
        new IllegalArgumentException().class.isAssignableFrom(SceneSpecException.canvas("x").class) ||
                SceneSpecException.canvas("x") instanceof IllegalArgumentException
    }

    def "canvas factory yields elementIndex -1 and null elementType"() {
        when:
        SceneSpecException e = SceneSpecException.canvas("bad")

        then:
        e.elementIndex() == -1
        e.elementType() == null
        e.message == "bad"
        e.cause == null
    }

    def "canvas factory propagates the cause"() {
        given:
        RuntimeException cause = new RuntimeException("inner")

        when:
        SceneSpecException e = SceneSpecException.canvas("outer", cause)

        then:
        e.cause.is(cause)
    }

    def "element factory carries index and type"() {
        when:
        SceneSpecException e = SceneSpecException.element(3, "plot", "no points")

        then:
        e.elementIndex() == 3
        e.elementType() == "plot"
        e.message == "no points"
    }
}
