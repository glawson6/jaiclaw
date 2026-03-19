package io.jclaw.compaction

import spock.lang.Specification

class IdentifierPreserverSpec extends Specification {

    IdentifierPreserver preserver = new IdentifierPreserver()

    def "extracts UUIDs from text"() {
        given:
        def text = "Session id is 550e8400-e29b-41d4-a716-446655440000"

        when:
        def ids = preserver.extractIdentifiers(text)

        then:
        ids.contains("550e8400-e29b-41d4-a716-446655440000")
    }

    def "extracts URLs from text"() {
        given:
        def text = "Visit https://example.com/api/v1 for docs"

        when:
        def ids = preserver.extractIdentifiers(text)

        then:
        ids.contains("https://example.com/api/v1")
    }

    def "extracts IP addresses from text"() {
        given:
        def text = "Server at 192.168.1.100"

        when:
        def ids = preserver.extractIdentifiers(text)

        then:
        ids.contains("192.168.1.100")
    }

    def "extracts file paths from text"() {
        given:
        def text = "File is at /usr/local/bin/jclaw"

        when:
        def ids = preserver.extractIdentifiers(text)

        then:
        ids.any { it.contains("/usr/local/bin") }
    }

    def "findMissing detects identifiers dropped during summarization"() {
        given:
        def original = "Config at https://example.com and 192.168.1.1"
        def summary = "Config details provided"

        when:
        def missing = preserver.findMissing(original, summary)

        then:
        missing.contains("https://example.com")
        missing.contains("192.168.1.1")
    }

    def "findMissing returns empty when all identifiers preserved"() {
        given:
        def original = "Config at https://example.com"
        def summary = "Config at https://example.com was discussed"

        when:
        def missing = preserver.findMissing(original, summary)

        then:
        missing.isEmpty()
    }

    def "handles null and empty text"() {
        expect:
        preserver.extractIdentifiers(null).isEmpty()
        preserver.extractIdentifiers("").isEmpty()
    }
}
