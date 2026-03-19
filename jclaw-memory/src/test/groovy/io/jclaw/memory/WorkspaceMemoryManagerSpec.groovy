package io.jclaw.memory

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class WorkspaceMemoryManagerSpec extends Specification {

    @TempDir
    Path tempDir

    WorkspaceMemoryManager manager

    def setup() {
        manager = new WorkspaceMemoryManager(tempDir)
    }

    def "readMemory returns empty for nonexistent file"() {
        expect:
        manager.readMemory() == ""
    }

    def "writeMemory and readMemory roundtrip"() {
        when:
        manager.writeMemory("# My Notes\n- Item 1\n")

        then:
        manager.readMemory() == "# My Notes\n- Item 1\n"
    }

    def "appendToSection creates section if missing"() {
        when:
        manager.appendToSection("Preferences", "Use dark mode")

        then:
        def content = manager.readMemory()
        content.contains("## Preferences")
        content.contains("- Use dark mode")
    }

    def "appendToSection appends to existing section"() {
        given:
        manager.writeMemory("## Preferences\n- Likes coffee\n")

        when:
        manager.appendToSection("Preferences", "Use dark mode")

        then:
        def content = manager.readMemory()
        content.contains("- Likes coffee")
        content.contains("- Use dark mode")
    }
}
