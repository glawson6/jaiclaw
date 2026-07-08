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

    def "getAvailableSlots (T1-5): tenant A's busy times don't block tenant B's availability"() {
        // Regression test for SEV-004: getAvailableSlots previously passed
        // null tenantId to listEvents, so every tenant's events were treated
        // as busy — leaking one tenant's occupancy into another's availability.
        given: "tenant A has a 2 PM meeting"
        def base = Instant.parse("2026-08-01T09:00:00Z")
        provider.createEvent(CalendarEvent.builder()
                .id("a-mtg")
                .tenantId("tenant-a")
                .title("Tenant A 2 PM meeting")
                .startTime(base.plusSeconds(5 * 3600))
                .endTime(base.plusSeconds(6 * 3600))
                .status(EventStatus.CONFIRMED)
                .build()).block()

        when: "tenant B queries availability for a 30-minute slot at 2 PM"
        def slots = null
        try {
            io.jaiclaw.core.tenant.TenantContextHolder.set(
                    new io.jaiclaw.core.tenant.DefaultTenantContext("tenant-b", "Tenant B"))
            slots = provider.getAvailableSlots(base, base.plusSeconds(9 * 3600), 1800)
                    .collectList().block()
        } finally {
            io.jaiclaw.core.tenant.TenantContextHolder.clear()
        }

        then: "2 PM is available for tenant B (tenant A's meeting is invisible)"
        // The whole 9-hour window should yield exactly one slot spanning
        // the full window because tenant B has no events in it. If leaked,
        // we'd see two slots split around tenant A's 2 PM meeting.
        slots.size() == 1
        slots[0].startTime() == base
        slots[0].endTime() == base.plusSeconds(9 * 3600)
    }
}
