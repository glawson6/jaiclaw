package io.jaiclaw.docstore.model

import spock.lang.Specification

import java.time.Instant

/**
 * 0.8.0 P3.6: exercises the entry record's compact constructor, all
 * with-mutators, the display helpers, and the Builder for both
 * {@link DocStoreEntry} and {@link AddRequest}.
 */
class DocStoreEntrySpec extends Specification {

    def "compact constructor defaults null tags to empty set"() {
        when:
        DocStoreEntry e = new DocStoreEntry(
                "id1", DocStoreEntry.EntryType.FILE, "f.pdf", "application/pdf",
                1024L, null, "telegram", "ref", "msg", "user", "chat",
                Instant.parse("2025-01-01T00:00:00Z"),
                null, "desc", "cat", null, "tenant"
        )

        then:
        e.tags() == [] as Set
    }

    def "compact constructor defaults null indexedAt to now"() {
        when:
        DocStoreEntry e = new DocStoreEntry(
                "id1", DocStoreEntry.EntryType.FILE, "f.pdf", null, 0L,
                null, "ch", null, null, null, null, null,
                ["t1"] as Set, null, null, null, null
        )

        then:
        e.indexedAt() != null
    }

    def "withTags returns new entry with updated tags only"() {
        given:
        DocStoreEntry e = baseEntry()

        when:
        DocStoreEntry updated = e.withTags(["new"] as Set)

        then:
        updated.tags() == ["new"] as Set
        updated.id() == e.id()
        updated.filename() == e.filename()
    }

    def "withDescription returns new entry with updated description"() {
        given:
        DocStoreEntry e = baseEntry()

        when:
        DocStoreEntry updated = e.withDescription("new desc")

        then:
        updated.description() == "new desc"
        updated.tags() == e.tags()
    }

    def "withCategory + withAnalysis + withTenantId mutators all work"() {
        given:
        DocStoreEntry e = baseEntry()
        AnalysisResult ar = AnalysisResult.builder()
                .summary("s")
                .extractedText("t")
                .topics(["topic"])
                .entities(["entity"])
                .metadata(["k": "v"])
                .level(AnalysisResult.AnalysisLevel.BASIC)
                .build()

        expect:
        e.withCategory("c2").category() == "c2"
        e.withAnalysis(ar).analysis() == ar
        e.withTenantId("t2").tenantId() == "t2"
    }

    def "displayName returns filename for FILE entry"() {
        given:
        DocStoreEntry e = baseEntry()

        expect:
        e.displayName() == "f.pdf"
    }

    def "displayName returns sourceUrl for URL entry"() {
        given:
        DocStoreEntry e = baseEntry().withCategory("c") // need a URL-type — build via builder
        DocStoreEntry url = DocStoreEntry.builder()
                .id("id2")
                .entryType(DocStoreEntry.EntryType.URL)
                .sourceUrl("https://example.test/x")
                .build()

        expect:
        url.displayName() == "https://example.test/x"
    }

    def "displayName returns 'fwd: filename' for FORWARDED entry"() {
        given:
        DocStoreEntry fwd = DocStoreEntry.builder()
                .id("id3")
                .entryType(DocStoreEntry.EntryType.FORWARDED)
                .filename("doc.txt")
                .build()

        expect:
        fwd.displayName() == "fwd: doc.txt"
    }

    def "displayName falls back to 'unnamed-X' when source is missing"() {
        expect:
        DocStoreEntry.builder().id("x").entryType(DocStoreEntry.EntryType.FILE).build().displayName() == "unnamed-file"
        DocStoreEntry.builder().id("y").entryType(DocStoreEntry.EntryType.URL).build().displayName() == "unnamed-url"
        DocStoreEntry.builder().id("z").entryType(DocStoreEntry.EntryType.FORWARDED).build().displayName() == "fwd: unnamed"
    }

    def "shortId truncates to 6 chars when long"() {
        expect:
        DocStoreEntry.builder().id("abcdefghij").build().shortId() == "abcdef"
    }

    def "shortId returns id unchanged when short"() {
        expect:
        DocStoreEntry.builder().id("abc").build().shortId() == "abc"
    }

    def "shortId handles null id"() {
        expect:
        DocStoreEntry.builder().build().shortId() == null
    }

    def "Builder exercises every setter"() {
        given:
        Instant t = Instant.parse("2025-06-01T12:00:00Z")
        AnalysisResult ar = AnalysisResult.builder()
                .summary("s")
                .extractedText("t")
                .topics(["topic"])
                .entities(["entity"])
                .metadata(["k": "v"])
                .level(AnalysisResult.AnalysisLevel.BASIC)
                .build()

        when:
        DocStoreEntry e = DocStoreEntry.builder()
                .id("id")
                .entryType(DocStoreEntry.EntryType.FILE)
                .filename("a.txt")
                .mimeType("text/plain")
                .fileSize(42L)
                .sourceUrl(null)
                .channelId("telegram")
                .channelFileRef("fref")
                .channelMessageRef("mref")
                .userId("user")
                .chatId("chat")
                .indexedAt(t)
                .tags(["tag1"] as Set)
                .description("d")
                .category("c")
                .analysis(ar)
                .tenantId("acme")
                .build()

        then:
        e.id() == "id"
        e.entryType() == DocStoreEntry.EntryType.FILE
        e.filename() == "a.txt"
        e.mimeType() == "text/plain"
        e.fileSize() == 42L
        e.channelId() == "telegram"
        e.userId() == "user"
        e.chatId() == "chat"
        e.indexedAt() == t
        e.tags() == ["tag1"] as Set
        e.description() == "d"
        e.category() == "c"
        e.analysis() == ar
        e.tenantId() == "acme"
    }

    def "EntryType enum has the expected values"() {
        expect:
        DocStoreEntry.EntryType.values() as Set == [
                DocStoreEntry.EntryType.FILE,
                DocStoreEntry.EntryType.URL,
                DocStoreEntry.EntryType.FORWARDED
        ] as Set
        DocStoreEntry.EntryType.valueOf("FILE") == DocStoreEntry.EntryType.FILE
    }

    def "AddRequest.Builder exercises every setter"() {
        when:
        AddRequest req = AddRequest.builder()
                .filename("f")
                .mimeType("m")
                .fileSize(10L)
                .content("hello".bytes)
                .channelId("ch")
                .channelFileRef("fr")
                .channelMessageRef("mr")
                .userId("u")
                .chatId("c")
                .sourceUrl(null)
                .entryType(DocStoreEntry.EntryType.FILE)
                .tags(["a"] as Set)
                .description("d")
                .build()

        then:
        req.filename() == "f"
        req.fileSize() == 10L
        req.userId() == "u"
        req.tags() == ["a"] as Set
        req.description() == "d"
        req.entryType() == DocStoreEntry.EntryType.FILE
    }

    def "AddRequest compact constructor defaults null tags to empty set"() {
        when:
        AddRequest req = new AddRequest(
                "f", "m", 1L, "x".bytes, "ch", null, null, "u", "c", null,
                DocStoreEntry.EntryType.FILE, null, "d"
        )

        then:
        req.tags() == [] as Set
    }

    private static DocStoreEntry baseEntry() {
        return new DocStoreEntry(
                "abcdef-1234", DocStoreEntry.EntryType.FILE, "f.pdf", "application/pdf",
                100L, null, "telegram", "fref", "mref", "user", "chat",
                Instant.parse("2025-01-01T00:00:00Z"),
                ["orig"] as Set, "desc", "cat", null, "tenant"
        )
    }
}
