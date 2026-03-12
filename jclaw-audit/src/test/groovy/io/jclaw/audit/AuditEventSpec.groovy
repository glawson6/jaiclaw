package io.jclaw.audit

import spock.lang.Specification

class AuditEventSpec extends Specification {

    def "success factory creates event with SUCCESS outcome"() {
        when:
        def event = AuditEvent.success("evt-1", "tenant-1", "user@test.com", "message.sent", "session:123")

        then:
        event.id() == "evt-1"
        event.tenantId() == "tenant-1"
        event.actor() == "user@test.com"
        event.action() == "message.sent"
        event.resource() == "session:123"
        event.outcome() == AuditEvent.Outcome.SUCCESS
        event.timestamp() != null
        event.details() == [:]
    }

    def "failure factory includes reason in details"() {
        when:
        def event = AuditEvent.failure("evt-2", "t1", "agent", "tool.executed", "web-search", "timeout")

        then:
        event.outcome() == AuditEvent.Outcome.FAILURE
        event.details().reason == "timeout"
    }

    def "rejects blank id"() {
        when:
        new AuditEvent("", null, null, null, "test.action", null, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects blank action"() {
        when:
        new AuditEvent("id-1", null, null, null, "", null, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "defaults null fields"() {
        when:
        def event = new AuditEvent("id-1", null, null, null, "test.action", null, null, null)

        then:
        event.actor() == "system"
        event.resource() == ""
        event.outcome() == AuditEvent.Outcome.SUCCESS
        event.details() == [:]
        event.timestamp() != null
    }
}
