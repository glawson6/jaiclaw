package io.jclaw.documents

import spock.lang.Specification

class HtmlDocumentParserSpec extends Specification {

    def parser = new HtmlDocumentParser()

    def "supports text/html and application/xhtml+xml"() {
        expect:
        parser.supports("text/html")
        parser.supports("application/xhtml+xml")
        !parser.supports("application/pdf")
        !parser.supports(null)
    }

    def "parse extracts body text and strips tags"() {
        given:
        def html = """
        <html>
        <head><title>Test Page</title></head>
        <body>
            <h1>Hello World</h1>
            <p>This is a test paragraph.</p>
            <script>alert('ignored')</script>
        </body>
        </html>
        """

        when:
        def result = parser.parse(html.bytes, "text/html")

        then:
        result.text().contains("Hello World")
        result.text().contains("This is a test paragraph")
        !result.text().contains("alert")
        !result.text().contains("<h1>")
        result.metadata().get("title") == "Test Page"
    }

    def "parse removes nav, footer, header, style elements"() {
        given:
        def html = """
        <html><body>
            <nav>Navigation</nav>
            <header>Header</header>
            <main>Main content here.</main>
            <footer>Footer</footer>
            <style>.hidden { display: none; }</style>
        </body></html>
        """

        when:
        def result = parser.parse(html.bytes, "text/html")

        then:
        result.text().contains("Main content here")
        !result.text().contains("Navigation")
        !result.text().contains("Footer")
    }
}
