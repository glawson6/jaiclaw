package io.jaiclaw.channel.process

import spock.lang.Specification

class CliProcessConfigSpec extends Specification {

    def "rejects null command"() {
        when:
        new CliProcessConfig(null, List.of(), null, 0, 30, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects blank command"() {
        when:
        new CliProcessConfig("  ", List.of(), null, 0, 30, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects workingDir with path traversal"() {
        when:
        new CliProcessConfig("echo", List.of(), "../../etc", 0, 30, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects workingDir with embedded .. traversal"() {
        when:
        new CliProcessConfig("echo", List.of(), "/home/user/../../../etc", 0, 30, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "accepts null workingDir"() {
        when:
        def config = new CliProcessConfig("echo", List.of(), null, 0, 30, 0)

        then:
        config.workingDir() == null
    }

    def "accepts valid absolute workingDir"() {
        when:
        def config = new CliProcessConfig("echo", List.of(), "/tmp", 0, 30, 0)

        then:
        config.workingDir() == "/tmp"
    }

    def "normalizes workingDir to absolute path"() {
        when:
        def config = new CliProcessConfig("echo", List.of(), "/tmp/foo/./bar", 0, 30, 0)

        then:
        config.workingDir() == "/tmp/foo/bar"
    }

    def "builder rejects path traversal"() {
        when:
        CliProcessConfig.builder()
            .command("echo")
            .workingDir("../../../etc")
            .build()

        then:
        thrown(IllegalArgumentException)
    }
}
