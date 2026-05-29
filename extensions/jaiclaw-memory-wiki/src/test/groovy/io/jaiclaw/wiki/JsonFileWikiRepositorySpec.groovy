package io.jaiclaw.wiki

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class JsonFileWikiRepositorySpec extends Specification {

    @TempDir
    Path tempDir

    JsonFileWikiRepository repo

    def setup() {
        repo = new JsonFileWikiRepository(tempDir)
    }

    def "save and find by id"() {
        given:
        def page = new WikiPage("p1", "Title", "cat", ["tag1"], "body", Map.of(),
                Instant.now(), Instant.now(), null)

        when:
        repo.save(page)

        then:
        repo.findById("p1").isPresent()
        repo.findById("p1").get().title() == "Title"
    }

    def "find by title is case insensitive"() {
        given:
        def page = new WikiPage("p1", "My Page", null, [], "body", Map.of(),
                Instant.now(), Instant.now(), null)
        repo.save(page)

        expect:
        repo.findByTitle("my page").isPresent()
    }

    def "find by category"() {
        given:
        repo.save(new WikiPage("p1", "T1", "docs", [], "", Map.of(), Instant.now(), Instant.now(), null))
        repo.save(new WikiPage("p2", "T2", "notes", [], "", Map.of(), Instant.now(), Instant.now(), null))

        expect:
        repo.findByCategory("docs").size() == 1
    }

    def "delete removes page"() {
        given:
        repo.save(new WikiPage("p1", "T1", null, [], "", Map.of(), Instant.now(), Instant.now(), null))

        when:
        repo.deleteById("p1")

        then:
        repo.findById("p1").isEmpty()
    }

    def "persists to disk and reloads"() {
        given:
        repo.save(new WikiPage("p1", "Persisted", null, ["t1"], "content", Map.of(),
                Instant.now(), Instant.now(), null))

        when:
        def repo2 = new JsonFileWikiRepository(tempDir)

        then:
        repo2.findById("p1").isPresent()
        repo2.findById("p1").get().title() == "Persisted"
    }
}
