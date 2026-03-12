package io.jclaw.audit

import spock.lang.Specification

class InMemoryAuditLoggerSpec extends Specification {

    def "logs and queries events"() {
        given:
        def logger = new InMemoryAuditLogger()

        when:
        logger.log(AuditEvent.success("e1", "t1", "user1", "action.a", "res1"))
        logger.log(AuditEvent.success("e2", "t1", "user2", "action.b", "res2"))
        logger.log(AuditEvent.success("e3", "t2", "user3", "action.c", "res3"))

        then:
        logger.size() == 3
        logger.count("t1") == 2
        logger.count("t2") == 1
    }

    def "query filters by tenant and limits"() {
        given:
        def logger = new InMemoryAuditLogger()
        logger.log(AuditEvent.success("e1", "t1", "u", "a", "r"))
        logger.log(AuditEvent.success("e2", "t2", "u", "a", "r"))
        logger.log(AuditEvent.success("e3", "t1", "u", "a", "r"))

        when:
        def t1Events = logger.query("t1", 10)

        then:
        t1Events.size() == 2
        t1Events.every { it.tenantId() == "t1" }
    }

    def "query with null tenantId returns all"() {
        given:
        def logger = new InMemoryAuditLogger()
        logger.log(AuditEvent.success("e1", "t1", "u", "a", "r"))
        logger.log(AuditEvent.success("e2", "t2", "u", "a", "r"))

        expect:
        logger.query(null, 100).size() == 2
    }

    def "findById returns event"() {
        given:
        def logger = new InMemoryAuditLogger()
        logger.log(AuditEvent.success("e1", "t1", "u", "a", "r"))

        expect:
        logger.findById("e1").isPresent()
        logger.findById("e1").get().id() == "e1"
        !logger.findById("missing").isPresent()
    }

    def "respects max size limit"() {
        given:
        def logger = new InMemoryAuditLogger(3)

        when:
        (1..5).each { i ->
            logger.log(AuditEvent.success("e$i", "t1", "u", "a", "r"))
        }

        then:
        logger.size() == 3
    }

    def "most recent events first"() {
        given:
        def logger = new InMemoryAuditLogger()
        logger.log(AuditEvent.success("first", "t1", "u", "a", "r"))
        logger.log(AuditEvent.success("second", "t1", "u", "a", "r"))

        when:
        def events = logger.query(null, 10)

        then:
        events[0].id() == "second"
        events[1].id() == "first"
    }

    def "clear removes all events"() {
        given:
        def logger = new InMemoryAuditLogger()
        logger.log(AuditEvent.success("e1", "t1", "u", "a", "r"))
        logger.log(AuditEvent.success("e2", "t1", "u", "a", "r"))

        when:
        logger.clear()

        then:
        logger.size() == 0
    }
}
