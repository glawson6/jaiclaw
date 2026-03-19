package io.jclaw.browser

import spock.lang.Specification

class PageSnapshotSpec extends Specification {

    def "toText formats page snapshot correctly"() {
        given:
        def elements = [
            new PageSnapshot.PageElement(1, "heading", "Welcome", null, null),
            new PageSnapshot.PageElement(2, "link", "Click here", null, "https://example.com"),
            new PageSnapshot.PageElement(3, "input", "search", "hello", null)
        ]
        def snapshot = new PageSnapshot("https://example.com", "Example", elements)

        when:
        def text = snapshot.toText()

        then:
        text.contains("URL: https://example.com")
        text.contains("Title: Example")
        text.contains('[1] heading "Welcome"')
        text.contains('[2] link "Click here" href="https://example.com"')
        text.contains('[3] input "search" value="hello"')
    }

    def "empty elements produces minimal output"() {
        given:
        def snapshot = new PageSnapshot("https://test.com", "Test", [])

        when:
        def text = snapshot.toText()

        then:
        text.contains("URL: https://test.com")
        text.contains("Title: Test")
    }
}
