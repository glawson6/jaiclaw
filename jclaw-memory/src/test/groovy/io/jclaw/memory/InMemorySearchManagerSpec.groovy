package io.jclaw.memory

import spock.lang.Specification

class InMemorySearchManagerSpec extends Specification {

    InMemorySearchManager manager = new InMemorySearchManager()

    def "search returns empty for no entries"() {
        expect:
        manager.search("anything").isEmpty()
    }

    def "search finds matching entry"() {
        given:
        manager.addEntry("file1.txt", "The quick brown fox jumps over the lazy dog")

        when:
        def results = manager.search("brown fox")

        then:
        results.size() == 1
        results[0].path() == "file1.txt"
        results[0].score() > 0
        results[0].snippet().contains("brown fox")
    }

    def "search is case insensitive"() {
        given:
        manager.addEntry("readme.md", "Hello World from JClaw")

        when:
        def results = manager.search("HELLO jclaw")

        then:
        results.size() == 1
    }

    def "search returns empty for no match"() {
        given:
        manager.addEntry("file.txt", "something completely different")

        expect:
        manager.search("quantum physics").isEmpty()
    }

    def "search respects maxResults option"() {
        given:
        (1..10).each { manager.addEntry("file${it}.txt", "matching content here") }
        def options = new MemorySearchOptions(3, 0.0, null)

        when:
        def results = manager.search("matching content", options)

        then:
        results.size() == 3
    }

    def "search respects minScore option"() {
        given:
        manager.addEntry("partial.txt", "only first word matches here")
        manager.addEntry("full.txt", "first second third all match")
        def options = new MemorySearchOptions(10, 0.9, null)

        when:
        def results = manager.search("first second third", options)

        then:
        results.every { it.score() >= 0.9 }
    }

    def "search ranks by score descending"() {
        given:
        manager.addEntry("low.txt", "just one match term here")
        manager.addEntry("high.txt", "match term and another match phrase")

        when:
        def results = manager.search("match term")

        then:
        results.size() == 2
        results[0].score() >= results[1].score()
    }

    def "search with blank query returns empty"() {
        given:
        manager.addEntry("file.txt", "content")

        expect:
        manager.search("").isEmpty()
        manager.search("  ").isEmpty()
        manager.search(null).isEmpty()
    }

    def "addEntry with source tracks MemorySource"() {
        given:
        manager.addEntry("session.txt", "session data", MemorySource.SESSIONS)

        when:
        def results = manager.search("session")

        then:
        results[0].source() == MemorySource.SESSIONS
    }

    def "clear removes all entries"() {
        given:
        manager.addEntry("a.txt", "content a")
        manager.addEntry("b.txt", "content b")

        when:
        manager.clear()

        then:
        manager.size() == 0
        manager.search("content").isEmpty()
    }

    def "size returns entry count"() {
        expect:
        manager.size() == 0

        when:
        manager.addEntry("a.txt", "content")
        manager.addEntry("b.txt", "content")

        then:
        manager.size() == 2
    }
}
