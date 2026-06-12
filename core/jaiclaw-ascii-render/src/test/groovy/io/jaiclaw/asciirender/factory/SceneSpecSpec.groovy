package io.jaiclaw.asciirender.factory

import spock.lang.Specification

class SceneSpecSpec extends Specification {

    def "happy path: builds an immutable scene from valid args"() {
        given:
        ElementSpec rect = ElementSpec.of("rectangle")

        when:
        SceneSpec scene = new SceneSpec(40, 10, [rect], false)

        then:
        scene.width() == 40
        scene.height() == 10
        scene.elements().size() == 1
        scene.elements()[0].type() == "rectangle"
        !scene.trim()
    }

    def "convenience constructor defaults trim to true"() {
        when:
        SceneSpec scene = new SceneSpec(10, 4, [])

        then:
        scene.trim()
    }

    def "null elements list normalises to empty"() {
        when:
        SceneSpec scene = new SceneSpec(10, 4, null, true)

        then:
        scene.elements() == []
    }

    def "elements list is defensively copied"() {
        given:
        List<ElementSpec> mutable = [ElementSpec.of("dot")]

        when:
        SceneSpec scene = new SceneSpec(10, 10, mutable)
        mutable.clear()

        then:
        scene.elements().size() == 1
    }

    def "non-positive width throws SceneSpecException with elementIndex -1"() {
        when:
        new SceneSpec(0, 10, [])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == -1
        ex.message.contains("width")
    }

    def "non-positive height throws SceneSpecException with elementIndex -1"() {
        when:
        new SceneSpec(10, -1, [])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == -1
        ex.message.contains("height")
    }
}
