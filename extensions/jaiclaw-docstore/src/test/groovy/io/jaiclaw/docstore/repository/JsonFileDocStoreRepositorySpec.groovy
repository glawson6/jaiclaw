package io.jaiclaw.docstore.repository

import io.jaiclaw.docstore.model.DocStoreEntry
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 0.8.0 P3.6: exercises the JSON-file-backed repository: load + save
 * + delete + update + every query method, plus the on-disk persistence
 * round-trip (write, drop the in-memory map by constructing a fresh
 * instance over the same directory, read back).
 */
class JsonFileDocStoreRepositorySpec extends Specification {

    @TempDir
    Path tmp

    JsonFileDocStoreRepository repo() {
        return new JsonFileDocStoreRepository(tmp)
    }

    private DocStoreEntry entry(String id, String userId, String chatId,
                                Set<String> tags, String mime, Instant indexedAt) {
        return new DocStoreEntry(
                id, DocStoreEntry.EntryType.FILE, id + ".txt", mime, 200L,
                null, "telegram", "fref", "mref", userId, chatId,
                indexedAt, tags, "desc", "cat", null, null
        )
    }

    def "save persists to disk and a fresh instance reloads it"() {
        given:
        JsonFileDocStoreRepository r1 = repo()
        Instant t = Instant.parse("2025-01-01T00:00:00Z")
        r1.save(entry("id1", "u1", "c1", ["t"] as Set, "text/plain", t))

        when:
        JsonFileDocStoreRepository r2 = repo()

        then:
        r2.findById("id1").isPresent()
        r2.findById("id1").get().filename() == "id1.txt"
    }

    def "delete persists removal across reload"() {
        given:
        JsonFileDocStoreRepository r1 = repo()
        Instant t = Instant.now()
        r1.save(entry("id2", "u1", "c1", [] as Set, "text/plain", t))
        r1.deleteById("id2")

        when:
        JsonFileDocStoreRepository r2 = repo()

        then:
        r2.findById("id2").isEmpty()
    }

    def "update persists mutation across reload"() {
        given:
        JsonFileDocStoreRepository r1 = repo()
        r1.save(entry("id3", "u1", "c1", [] as Set, "text/plain", Instant.now()))

        when:
        r1.update("id3") { it.withCategory("cat2") }
        JsonFileDocStoreRepository r2 = repo()

        then:
        r2.findById("id3").get().category() == "cat2"
    }

    def "update on missing id returns null and doesn't flush"() {
        given:
        JsonFileDocStoreRepository r = repo()

        expect:
        r.update("never-existed") { it } == null
    }

    def "findRecent + count + findByTags + findByMimeTypePrefix all work end-to-end"() {
        given:
        JsonFileDocStoreRepository r = repo()
        Instant base = Instant.parse("2025-01-01T00:00:00Z")
        r.save(entry("a", "u1", "c1", ["tag1"] as Set, "image/png", base))
        r.save(entry("b", "u1", "c1", ["tag2"] as Set, "image/jpeg", base.plus(1, ChronoUnit.MINUTES)))
        r.save(entry("c", "u2", "c2", ["tag1"] as Set, "text/plain", base.plus(2, ChronoUnit.MINUTES)))

        expect:
        r.findRecent(null, 2)*.id() == ["c", "b"]
        r.count(null) == 3L
        r.count("u1") == 2L
        r.findByTags(["tag1"] as Set, null)*.id() as Set == ["a", "c"] as Set
        r.findByMimeTypePrefix("image/", null)*.id() as Set == ["a", "b"] as Set
        r.findByUserId("u1", 10, 0).size() == 2
        r.findByChatId("c2", 10, 0)*.id() == ["c"]
    }

    def "constructor on empty directory does not throw"() {
        when:
        JsonFileDocStoreRepository r = repo()

        then:
        r.count(null) == 0L
    }

    def "two-arg constructor accepts null tenant guard and falls back"() {
        when:
        JsonFileDocStoreRepository r = new JsonFileDocStoreRepository(tmp, null)

        then:
        r.count(null) == 0L
    }

    def "load tolerates a missing docstore.json file"() {
        given:
        Path freshDir = Files.createTempDirectory("docstore-empty")
        try {
            when:
            JsonFileDocStoreRepository r = new JsonFileDocStoreRepository(freshDir)
            then:
            r.findById("anything").isEmpty()
        } finally {
            freshDir.toFile().deleteDir()
        }
    }
}
