package io.jaiclaw.audit

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class FileAuditLoggerSpec extends Specification {

    @TempDir
    Path tempDir

    def "log writes event as JSON line"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        AuditEvent event = AuditEvent.success("evt-1", null, "user", "test.action", "res")

        when:
        logger.log(event)

        then:
        Path defaultDir = tempDir.resolve("_default")
        Files.isDirectory(defaultDir)
        List<Path> files = Files.list(defaultDir).toList()
        files.size() == 1
        String content = Files.readString(files[0])
        content.contains("evt-1")
        content.contains("test.action")
    }

    def "log with tenant creates tenant subdirectory"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        AuditEvent event = AuditEvent.success("evt-1", "tenant-a", "user", "action", "res")

        when:
        logger.log(event)

        then:
        Files.isDirectory(tempDir.resolve("tenant-a"))
        !Files.exists(tempDir.resolve("_default"))
    }

    def "query returns events most-recent-first"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        logger.log(AuditEvent.success("evt-1", "t1", "user", "first", "res"))
        logger.log(AuditEvent.success("evt-2", "t1", "user", "second", "res"))

        when:
        List<AuditEvent> events = logger.query("t1", 10)

        then:
        events.size() == 2
        events[0].id() == "evt-2"
        events[1].id() == "evt-1"
    }

    def "query respects limit"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        logger.log(AuditEvent.success("evt-1", "t1", "user", "action", "res"))
        logger.log(AuditEvent.success("evt-2", "t1", "user", "action", "res"))
        logger.log(AuditEvent.success("evt-3", "t1", "user", "action", "res"))

        when:
        List<AuditEvent> events = logger.query("t1", 2)

        then:
        events.size() == 2
    }

    def "query returns empty list for unknown tenant"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)

        when:
        List<AuditEvent> events = logger.query("nonexistent", 10)

        then:
        events.isEmpty()
    }

    def "findById returns matching event"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        logger.log(AuditEvent.success("evt-1", null, "user", "action", "res"))
        logger.log(AuditEvent.success("evt-2", null, "user", "action", "res"))

        when:
        Optional<AuditEvent> found = logger.findById("evt-1")

        then:
        found.isPresent()
        found.get().id() == "evt-1"
    }

    def "findById returns empty for nonexistent event"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        logger.log(AuditEvent.success("evt-1", null, "user", "action", "res"))

        when:
        Optional<AuditEvent> found = logger.findById("nonexistent")

        then:
        found.isEmpty()
    }

    def "count returns number of events"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        logger.log(AuditEvent.success("evt-1", "t1", "user", "action", "res"))
        logger.log(AuditEvent.success("evt-2", "t1", "user", "action", "res"))
        logger.log(AuditEvent.success("evt-3", "t2", "user", "action", "res"))

        expect:
        logger.count("t1") == 2
        logger.count("t2") == 1
        logger.count("nonexistent") == 0
    }

    def "count returns 0 for empty store"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)

        expect:
        logger.count(null) == 0
    }

    def "multiple events on same date go to same file"() {
        given:
        FileAuditLogger logger = new FileAuditLogger(tempDir)
        logger.log(AuditEvent.success("evt-1", "t1", "user", "action1", "res"))
        logger.log(AuditEvent.success("evt-2", "t1", "user", "action2", "res"))

        when:
        List<Path> files = Files.list(tempDir.resolve("t1")).toList()

        then:
        files.size() == 1
        String content = Files.readString(files[0])
        content.contains("evt-1")
        content.contains("evt-2")
    }
}
