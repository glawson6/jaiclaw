package io.jaiclaw.calendar.provider

import io.jaiclaw.calendar.model.*
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import spock.lang.Specification

import java.time.Instant

class InMemoryCalendarProviderMultiTenantSpec extends Specification {

    InMemoryCalendarProvider provider

    def setup() {
        provider = new InMemoryCalendarProvider()
        provider.setTenantGuard(new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false)))
    }

    private CalendarEvent event(String tenant, String id, String title) {
        return CalendarEvent.builder()
                .id(id)
                .tenantId(tenant)
                .title(title)
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()
    }

    def "two tenants creating events with the same id do not collide"() {
        when:
        provider.createEvent(event("tenant-a", "evt-1", "Tenant A meeting")).block()
        provider.createEvent(event("tenant-b", "evt-1", "Tenant B meeting")).block()

        then:
        provider.getEventCount() == 2
    }

    def "getEvent only returns the requested tenant's event"() {
        given:
        provider.createEvent(event("tenant-a", "evt-1", "Tenant A meeting")).block()
        provider.createEvent(event("tenant-b", "evt-1", "Tenant B meeting")).block()

        when:
        CalendarEvent aResult = provider.getEvent("tenant-a", null, "evt-1").block()
        CalendarEvent bResult = provider.getEvent("tenant-b", null, "evt-1").block()

        then:
        aResult.title() == "Tenant A meeting"
        bResult.title() == "Tenant B meeting"
    }

    def "deleteEvent under tenant B does not remove tenant A's event"() {
        given:
        provider.createEvent(event("tenant-a", "evt-1", "Tenant A meeting")).block()
        provider.createEvent(event("tenant-b", "evt-1", "Tenant B meeting")).block()

        when:
        provider.deleteEvent("tenant-b", null, "evt-1").block()

        then:
        provider.getEvent("tenant-a", null, "evt-1").block().title() == "Tenant A meeting"
        provider.getEvent("tenant-b", null, "evt-1").block() == null
    }

    def "updateEvent only mutates the requested tenant's event"() {
        given:
        provider.createEvent(event("tenant-a", "evt-1", "Tenant A original")).block()
        provider.createEvent(event("tenant-b", "evt-1", "Tenant B original")).block()

        when:
        provider.updateEvent("tenant-a", null, "evt-1", [title: "Tenant A updated"]).block()

        then:
        provider.getEvent("tenant-a", null, "evt-1").block().title() == "Tenant A updated"
        provider.getEvent("tenant-b", null, "evt-1").block().title() == "Tenant B original"
    }
}
