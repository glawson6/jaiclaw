package io.jaiclaw.tools.exec

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class WorkspaceBoundarySpec extends Specification {

    @TempDir
    Path tempDir

    def "resolves relative path within workspace"() {
        when:
        def resolved = WorkspaceBoundary.resolve(tempDir.toString(), "src/main.java")

        then:
        resolved == tempDir.resolve("src/main.java").normalize()
    }

    def "resolves nested path within workspace"() {
        when:
        def resolved = WorkspaceBoundary.resolve(tempDir.toString(), "a/b/c/d.txt")

        then:
        resolved.startsWith(tempDir.toAbsolutePath().normalize())
    }

    def "allows path with . components"() {
        when:
        def resolved = WorkspaceBoundary.resolve(tempDir.toString(), "./src/../src/main.java")

        then:
        resolved == tempDir.resolve("src/main.java").normalize()
    }

    @Unroll
    def "blocks path traversal: #path"() {
        when:
        WorkspaceBoundary.resolve(tempDir.toString(), path)

        then:
        thrown(SecurityException)

        where:
        path << [
            "../../../etc/passwd",
            "../../.ssh/id_rsa",
            "src/../../../../../../etc/shadow",
            "/etc/passwd",
            "/tmp/evil",
        ]
    }

    def "blocks absolute path outside workspace"() {
        when:
        WorkspaceBoundary.resolve(tempDir.toString(), "/etc/passwd")

        then:
        thrown(SecurityException)
    }

    def "allows absolute path inside workspace"() {
        given:
        def absInside = tempDir.resolve("inside.txt").toAbsolutePath().toString()

        when:
        def resolved = WorkspaceBoundary.resolve(tempDir.toString(), absInside)

        then:
        resolved == tempDir.resolve("inside.txt").toAbsolutePath().normalize()
    }
}
