package io.jaiclaw.agentmind.soul.personas

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Plan §8 task 4.3 — PersonaOverlayManager spec.
 *
 * Covers: load from dir, list available, activate / clear, missing personas,
 * missing dir, session isolation, and reload picking up new files.
 */
class PersonaOverlayManagerSpec extends Specification {

    @TempDir
    Path tmp

    def "loads .md files and exposes them by stem"() {
        given:
        Files.writeString(tmp.resolve("concise.md"), "# Concise\nbe brief")
        Files.writeString(tmp.resolve("pirate.md"), "# Pirate\narrr")
        Files.writeString(tmp.resolve("README.txt"), "ignored")

        when:
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)

        then:
        mgr.available() == ["concise", "pirate"]
        mgr.exists("concise")
        mgr.exists("pirate")
        !mgr.exists("README")
    }

    def "non-existent directory yields no personas, no exception"() {
        when:
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp.resolve("does-not-exist"))

        then:
        mgr.available() == []
        !mgr.exists("anything")
    }

    def "activate sets the active persona; activeMarkdown returns the file body"() {
        given:
        Files.writeString(tmp.resolve("concise.md"), "be brief")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)

        when:
        boolean ok = mgr.activate("sess-1", "concise")

        then:
        ok
        mgr.activeMarkdown("sess-1").get() == "be brief"
        mgr.activeName("sess-1").get() == "concise"
    }

    def "activate of an unknown persona returns false and does not record"() {
        given:
        Files.writeString(tmp.resolve("concise.md"), "be brief")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)

        when:
        boolean ok = mgr.activate("sess-1", "ghost")

        then:
        !ok
        mgr.activeMarkdown("sess-1").isEmpty()
        mgr.activeName("sess-1").isEmpty()
    }

    def "clear removes the active persona for the given session only"() {
        given:
        Files.writeString(tmp.resolve("concise.md"), "be brief")
        Files.writeString(tmp.resolve("pirate.md"), "arrr")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)
        mgr.activate("sess-1", "concise")
        mgr.activate("sess-2", "pirate")

        when:
        mgr.clear("sess-1")

        then:
        mgr.activeMarkdown("sess-1").isEmpty()
        mgr.activeMarkdown("sess-2").get() == "arrr"
    }

    def "null sessionKey / personaName are rejected safely"() {
        given:
        Files.writeString(tmp.resolve("concise.md"), "be brief")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)

        expect:
        !mgr.activate(null, "concise")
        !mgr.activate("sess-1", null)
        mgr.activeMarkdown(null).isEmpty()
        mgr.activeName(null).isEmpty()
    }

    def "reload picks up newly-added persona files"() {
        given:
        Files.writeString(tmp.resolve("concise.md"), "be brief")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)

        when:
        Files.writeString(tmp.resolve("pirate.md"), "arrr")
        mgr.reload()

        then:
        mgr.available() == ["concise", "pirate"]
    }

    def "reload preserves active-session assignments; activeMarkdown follows current file"() {
        given:
        Path file = tmp.resolve("concise.md")
        Files.writeString(file, "v1")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)
        mgr.activate("sess-1", "concise")

        when:
        Files.writeString(file, "v2")
        mgr.reload()

        then:
        mgr.activeName("sess-1").get() == "concise"
        mgr.activeMarkdown("sess-1").get() == "v2"
    }

    def "removed persona from disk yields empty activeMarkdown but keeps activeName"() {
        given:
        Path file = tmp.resolve("concise.md")
        Files.writeString(file, "be brief")
        PersonaOverlayManager mgr = new PersonaOverlayManager(tmp)
        mgr.activate("sess-1", "concise")

        when:
        Files.delete(file)
        mgr.reload()

        then:
        mgr.activeMarkdown("sess-1").isEmpty()
        mgr.activeName("sess-1").get() == "concise"  // assignment unchanged; lookup just falls through
    }
}
