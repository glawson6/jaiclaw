package io.jaiclaw.asciirender.factory

import io.jaiclaw.asciirender.api.IContext
import io.jaiclaw.asciirender.core.Region
import io.jaiclaw.asciirender.core.Render
import io.jaiclaw.asciirender.element.Rectangle
import spock.lang.Specification

class AsciiSceneFactorySpec extends Specification {

    def "fromMap parses a simple scene end-to-end"() {
        given:
        Map<String, Object> raw = [
                width: 20,
                height: 5,
                elements: [
                        [type: "rectangle"],
                        [type: "label", params: [text: "hello", x: 6, y: 2]]
                ]
        ]

        when:
        SceneSpec scene = AsciiSceneFactory.fromMap(raw)

        then:
        scene.width() == 20
        scene.height() == 5
        scene.elements()*.type() == ["rectangle", "label"]
        scene.elements()[1].params().text == "hello"
        scene.trim()
    }

    def "fromMap defaults trim to true when key absent"() {
        when:
        SceneSpec scene = AsciiSceneFactory.fromMap([width: 5, height: 5, elements: []])

        then:
        scene.trim()
    }

    def "fromMap honours explicit trim=false"() {
        when:
        SceneSpec scene = AsciiSceneFactory.fromMap([width: 5, height: 5, elements: [], trim: false])

        then:
        !scene.trim()
    }

    def "fromMap rejects missing width"() {
        when:
        AsciiSceneFactory.fromMap([height: 5, elements: []])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == -1
        ex.message.contains("width")
    }

    def "fromMap rejects non-list elements"() {
        when:
        AsciiSceneFactory.fromMap([width: 5, height: 5, elements: "not a list"])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == -1
        ex.message.contains("must be a list")
    }

    def "fromMap reports missing type with element index"() {
        when:
        AsciiSceneFactory.fromMap([width: 5, height: 5, elements: [[params: [text: "x"]]]])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == 0
        ex.message.contains("'type'")
    }

    def "fromJson round-trips a known scene"() {
        given:
        String json = '''
            {"width": 30, "height": 4,
             "elements": [
               {"type": "rectangle"},
               {"type": "label", "params": {"text": "ok", "x": 13, "y": 1}}
             ]}'''

        when:
        SceneSpec scene = AsciiSceneFactory.fromJson(json)

        then:
        scene.width() == 30
        scene.height() == 4
        scene.elements()*.type() == ["rectangle", "label"]
    }

    def "fromJson surfaces malformed JSON as canvas-level SceneSpecException"() {
        when:
        AsciiSceneFactory.fromJson("{ this is not json")

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == -1
        ex.message.toLowerCase().contains("malformed")
    }

    def "fromJson rejects empty input"() {
        when:
        AsciiSceneFactory.fromJson("")

        then:
        thrown(SceneSpecException)
    }

    def "render(scene) produces a trimmed rectangle"() {
        given:
        SceneSpec scene = AsciiSceneFactory.fromMap(
                [width: 10, height: 4, elements: [[type: "rectangle"]]])

        when:
        String text = AsciiSceneFactory.render(scene)

        then:
        text.split("\n", -1)[0].startsWith("┌")
        text.split("\n", -1)[-1].endsWith("┘")
    }

    def "render(Map) matches a hand-built equivalent context"() {
        given:
        Map<String, Object> raw = [width: 10, height: 4, elements: [[type: "rectangle"]]]
        IContext manual = Render.builder()
                .width(10)
                .height(4)
                .layer(new Region(0, 0, 10, 4))
                .element(new Rectangle())
                .build()
        String manualOut = new Render().render(manual).trim().getText()

        expect:
        AsciiSceneFactory.render(raw) == manualOut
    }

    def "renderJson works for a JSON-described label"() {
        when:
        String text = AsciiSceneFactory.renderJson('''
            {"width": 20, "height": 5, "elements": [
              {"type": "rectangle"},
              {"type": "label", "params": {"text": "hi", "x": 8, "y": 2}}
            ]}''')

        then:
        text.contains("hi")
    }

    def "toContext lets callers add elements before rendering"() {
        given:
        SceneSpec scene = AsciiSceneFactory.fromMap(
                [width: 20, height: 5, elements: [[type: "rectangle"]]])

        when:
        IContext ctx = AsciiSceneFactory.toContext(scene)
        String text = new Render().render(ctx).trim().getText()

        then:
        text.split("\n", -1)[0].startsWith("┌")
    }

    def "unknown element type is reported with index and type"() {
        when:
        AsciiSceneFactory.render([width: 10, height: 4, elements: [[type: "marshmallow"]]])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == 0
        ex.elementType() == "marshmallow"
        ex.message.contains("Unknown element type")
    }

    def "per-element IllegalArgumentException is wrapped with index and type"() {
        when:
        AsciiSceneFactory.render([
                width   : 30,
                height  : 6,
                elements: [
                        [type: "rectangle"],
                        [type: "label", params: [text: "ok"]],
                        [type: "table", params: [rows: 0, columns: 0]]
                ]
        ])

        then:
        SceneSpecException ex = thrown()
        ex.elementIndex() == 2
        ex.elementType() == "table"
        ex.message.contains("positive")
    }
}
