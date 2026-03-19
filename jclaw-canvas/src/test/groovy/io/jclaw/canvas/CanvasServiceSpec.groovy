package io.jclaw.canvas

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CanvasServiceSpec extends Specification {

    @TempDir
    Path tempDir

    CanvasService service

    def setup() {
        def config = new CanvasConfig(true, 18793, "127.0.0.1", true)
        def fileManager = new CanvasFileManager(tempDir)
        service = new CanvasService(config, fileManager)
    }

    def "present writes HTML and returns URL"() {
        when:
        def url = service.present("<h1>Hello</h1>")

        then:
        url.startsWith("http://127.0.0.1:18793/")
        service.isVisible()
    }

    def "getCurrentContent returns presented HTML"() {
        given:
        service.present("<h1>Test</h1>")

        when:
        def content = service.getCurrentContent()

        then:
        content.isPresent()
        content.get() == "<h1>Test</h1>"
    }

    def "hide sets visible to false"() {
        given:
        service.present("<h1>Test</h1>")

        when:
        service.hide()

        then:
        !service.isVisible()
    }

    def "getCurrentContent is empty before any presentation"() {
        expect:
        service.getCurrentContent().isEmpty()
    }
}
