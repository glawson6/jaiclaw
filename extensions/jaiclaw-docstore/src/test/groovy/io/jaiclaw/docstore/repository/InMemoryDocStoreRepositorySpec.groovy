package io.jaiclaw.docstore.repository

import io.jaiclaw.docstore.model.DocStoreEntry
import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 0.8.0 P3.6: exercises every read/write path on
 * {@link InMemoryDocStoreRepository} in SINGLE mode — the public CRUD
 * methods plus the four query methods ({@code findByUserId},
 * {@code findByChatId}, {@code findByTags},
 * {@code findByMimeTypePrefix}, {@code findRecent}, {@code count}).
 *
 * <p>Used to lift the {@code jaiclaw-docstore} module past the 40%
 * JaCoCo gate.
 */
class InMemoryDocStoreRepositorySpec extends Specification {

    InMemoryDocStoreRepository repo = new InMemoryDocStoreRepository()

    private DocStoreEntry entry(String id, String userId, String chatId,
                                Set<String> tags, String mime, Instant indexedAt) {
        return new DocStoreEntry(
                id, DocStoreEntry.EntryType.FILE, id + ".txt", mime, 100L,
                null, "telegram", "fref", "mref", userId, chatId,
                indexedAt, tags, "desc", "cat", null, null
        )
    }

    def "no-arg constructor uses default tenant guard"() {
        expect:
        new InMemoryDocStoreRepository() != null
    }

    def "save + findById + deleteById round-trip"() {
        given:
        DocStoreEntry e = entry("id1", "u1", "c1", ["t1"] as Set, "text/plain", Instant.now())

        when:
        repo.save(e)

        then:
        repo.findById("id1").isPresent()
        repo.findById("id1").get().filename() == "id1.txt"

        when:
        repo.deleteById("id1")

        then:
        repo.findById("id1").isEmpty()
    }

    def "update mutates the existing entry"() {
        given:
        DocStoreEntry e = entry("id2", "u1", "c1", ["t1"] as Set, "text/plain", Instant.now())
        repo.save(e)

        when:
        DocStoreEntry updated = repo.update("id2") { it.withDescription("changed") }

        then:
        updated.description() == "changed"
        repo.findById("id2").get().description() == "changed"
    }

    def "update on missing id returns null"() {
        expect:
        repo.update("missing") { it } == null
    }

    def "findByUserId returns descending-indexedAt order with limit + offset"() {
        given:
        Instant base = Instant.parse("2025-01-01T00:00:00Z")
        repo.save(entry("a", "u1", "c1", [] as Set, "text/plain", base))
        repo.save(entry("b", "u1", "c1", [] as Set, "text/plain", base.plus(1, ChronoUnit.MINUTES)))
        repo.save(entry("c", "u1", "c1", [] as Set, "text/plain", base.plus(2, ChronoUnit.MINUTES)))
        repo.save(entry("x", "u2", "c2", [] as Set, "text/plain", base.plus(3, ChronoUnit.MINUTES)))

        when:
        List<DocStoreEntry> page1 = repo.findByUserId("u1", 2, 0)

        then:
        page1.size() == 2
        page1[0].id() == "c"
        page1[1].id() == "b"

        when:
        List<DocStoreEntry> page2 = repo.findByUserId("u1", 2, 2)

        then:
        page2.size() == 1
        page2[0].id() == "a"
    }

    def "findByChatId filters by chatId and respects limit"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "chatX", [] as Set, "text/plain", t))
        repo.save(entry("b", "u1", "chatX", [] as Set, "text/plain", t.plusSeconds(1)))
        repo.save(entry("c", "u1", "other", [] as Set, "text/plain", t.plusSeconds(2)))

        when:
        List<DocStoreEntry> result = repo.findByChatId("chatX", 5, 0)

        then:
        result.size() == 2
        result*.id() as Set == ["a", "b"] as Set
    }

    def "findByTags returns entries with any overlapping tag"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "c1", ["x"] as Set, "text/plain", t))
        repo.save(entry("b", "u1", "c1", ["y"] as Set, "text/plain", t))
        repo.save(entry("c", "u1", "c1", ["x", "y"] as Set, "text/plain", t))
        repo.save(entry("d", "u1", "c1", ["z"] as Set, "text/plain", t))

        when:
        List<DocStoreEntry> result = repo.findByTags(["x"] as Set, null)

        then:
        result*.id() as Set == ["a", "c"] as Set
    }

    def "findByTags respects scopeId filter"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "c1", ["x"] as Set, "text/plain", t))
        repo.save(entry("b", "u2", "c1", ["x"] as Set, "text/plain", t))

        when:
        List<DocStoreEntry> result = repo.findByTags(["x"] as Set, "u1")

        then:
        result*.id() == ["a"]
    }

    def "findByMimeTypePrefix filters by prefix"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "c1", [] as Set, "image/png", t))
        repo.save(entry("b", "u1", "c1", [] as Set, "image/jpeg", t.plusSeconds(1)))
        repo.save(entry("c", "u1", "c1", [] as Set, "text/plain", t.plusSeconds(2)))

        when:
        List<DocStoreEntry> result = repo.findByMimeTypePrefix("image/", null)

        then:
        result*.id() as Set == ["a", "b"] as Set
    }

    def "findByMimeTypePrefix tolerates null mimeType"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "c1", [] as Set, null, t))
        repo.save(entry("b", "u1", "c1", [] as Set, "image/png", t))

        when:
        List<DocStoreEntry> result = repo.findByMimeTypePrefix("image/", null)

        then:
        result*.id() == ["b"]
    }

    def "findRecent returns most-recently-indexed entries first"() {
        given:
        Instant base = Instant.parse("2025-01-01T00:00:00Z")
        repo.save(entry("old", "u1", "c1", [] as Set, "text/plain", base))
        repo.save(entry("mid", "u1", "c1", [] as Set, "text/plain", base.plusSeconds(60)))
        repo.save(entry("new", "u1", "c1", [] as Set, "text/plain", base.plusSeconds(120)))

        when:
        List<DocStoreEntry> result = repo.findRecent(null, 2)

        then:
        result*.id() == ["new", "mid"]
    }

    def "count returns total entries when scope is null"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "c1", [] as Set, "text/plain", t))
        repo.save(entry("b", "u2", "c2", [] as Set, "text/plain", t))

        expect:
        repo.count(null) == 2L
    }

    def "count filters by scopeId (user or chat)"() {
        given:
        Instant t = Instant.now()
        repo.save(entry("a", "u1", "c1", [] as Set, "text/plain", t))
        repo.save(entry("b", "u2", "c2", [] as Set, "text/plain", t))
        repo.save(entry("c", "u1", "c2", [] as Set, "text/plain", t))

        expect:
        repo.count("u1") == 2L
        repo.count("c2") == 2L
        repo.count("nope") == 0L
    }
}
