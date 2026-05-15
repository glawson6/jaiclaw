package io.jaiclaw.canvas

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CanvasFileManagerSpec extends Specification {

    @TempDir
    Path tempDir

    CanvasFileManager fileManager

    def setup() {
        fileManager = new CanvasFileManager(tempDir)
    }

    def "writes and reads HTML with normal UUID-style ID"() {
        given:
        def id = UUID.randomUUID().toString()
        def html = "<h1>Test</h1>"

        when:
        def fileName = fileManager.writeHtml(id, html)

        then:
        fileName == id + ".html"
        fileManager.readHtml(fileName).isPresent()
        fileManager.readHtml(fileName).get() == html
    }

    def "writeHtml with no-arg variant generates UUID and works"() {
        when:
        def fileName = fileManager.writeHtml("<p>auto-id</p>")

        then:
        fileName.endsWith(".html")
        fileManager.readHtml(fileName).isPresent()
        fileManager.readHtml(fileName).get() == "<p>auto-id</p>"
    }

    def "writeHtml blocks path traversal with ../"() {
        when:
        fileManager.writeHtml("../../../etc/passwd", "malicious")

        then:
        thrown(SecurityException)
    }

    def "writeHtml blocks path traversal with forward slash"() {
        when:
        fileManager.writeHtml("foo/bar", "<p>test</p>")

        then:
        thrown(SecurityException)
    }

    def "writeHtml blocks path traversal with backslash"() {
        when:
        fileManager.writeHtml("foo\\bar", "<p>test</p>")

        then:
        thrown(SecurityException)
    }

    def "readHtml blocks path traversal"() {
        when:
        def result = fileManager.readHtml("../../../etc/passwd")

        then:
        result.isEmpty()
    }

    def "readHtml returns empty for nonexistent file"() {
        expect:
        fileManager.readHtml("nonexistent.html").isEmpty()
    }
}
