package io.jaiclaw.kanban.render

import io.jaiclaw.kanban.model.BoardSnapshot
import io.jaiclaw.kanban.model.CardView
import spock.lang.Specification

import java.time.Instant

class BoardAsciiRendererSpec extends Specification {

    private CardView card(String id, String name, String description, String state) {
        new CardView(id, name, description, "demo", state, null, 0L, 0,
                Instant.parse("2026-06-12T00:00:00Z"), null, null, [])
    }

    private BoardSnapshot snapshot() {
        new BoardSnapshot("demo", "Demo Board", Instant.parse("2026-06-12T00:00:00Z"),
                [
                        new BoardSnapshot.ColumnSnapshot("backlog", "Backlog", null, false, [
                                card("a1f2-blog",  "Q3 Blog Post",  "Long-form blog about Q3 launch", "backlog"),
                                card("77ab-docs",  "Docs Refresh",  "Refresh API reference pages",     "backlog"),
                        ]),
                        new BoardSnapshot.ColumnSnapshot("drafting", "Drafting", 3, false, [
                                card("c9d1-news",  "News Letter v2", "Monthly newsletter draft v2",   "drafting"),
                        ]),
                        new BoardSnapshot.ColumnSnapshot("review", "Review", 2, false, []),
                        new BoardSnapshot.ColumnSnapshot("done", "Done", null, true, [
                                card("f0a9-tos",   "TOS Update",    "Approved by legal",              "done"),
                        ]),
                ],
                4)
    }

    def "renders the full-style board against the golden snapshot"() {
        given:
        def renderer = new BoardAsciiRenderer()
        def options = new AsciiBoardOptions(80, AsciiBoardOptions.Style.FULL, 2, true, "(empty)")

        when:
        def actual = renderer.render(snapshot(), options)

        then:
        def golden = this.class.getResourceAsStream("/boards/golden/demo-board-full.txt").text
        // Trim trailing whitespace per line for cross-platform stability; preserves shape.
        normalize(actual) == normalize(golden)
    }

    def "renders the compact-style board against the golden snapshot"() {
        given:
        def renderer = new BoardAsciiRenderer()
        def options = new AsciiBoardOptions(80, AsciiBoardOptions.Style.COMPACT, 2, true, "(empty)")

        when:
        def actual = renderer.render(snapshot(), options)

        then:
        def golden = this.class.getResourceAsStream("/boards/golden/demo-board-compact.txt").text
        normalize(actual) == normalize(golden)
    }

    def "an empty column shows the configured empty marker"() {
        given:
        def renderer = new BoardAsciiRenderer()
        def snap = new BoardSnapshot("e", "Empty Demo", null,
                [new BoardSnapshot.ColumnSnapshot("only", "Only", null, false, [])], 0)

        when:
        def out = renderer.render(snap, new AsciiBoardOptions(40, AsciiBoardOptions.Style.FULL, 1, true, "(empty)"))

        then:
        out.contains("(empty)")
    }

    def "a long board name in the title bar is clamped"() {
        given:
        def renderer = new BoardAsciiRenderer()
        def snap = new BoardSnapshot("x",
                "A very very very very very very long board name", null,
                [new BoardSnapshot.ColumnSnapshot("a", "A", null, false, [])], 0)

        when:
        def out = renderer.render(snap, new AsciiBoardOptions(30, AsciiBoardOptions.Style.FULL, 1, true, "(empty)"))

        then: "doesn't crash through the corner"
        out.split("\n")[0].length() == 30
    }

    def "renders an empty board without throwing"() {
        given:
        def renderer = new BoardAsciiRenderer()
        def snap = new BoardSnapshot("z", "Z", null, [], 0)

        expect:
        !renderer.render(snap).isBlank()
    }

    private static String normalize(String text) {
        text.split("\\r?\\n").collect { it.replaceAll("[ \\t]+\$", "") }.join("\n")
    }
}
