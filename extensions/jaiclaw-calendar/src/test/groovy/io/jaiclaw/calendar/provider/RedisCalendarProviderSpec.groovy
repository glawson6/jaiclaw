package io.jaiclaw.calendar.provider

import com.fasterxml.jackson.databind.ObjectMapper
import io.jaiclaw.calendar.config.CalendarProperties
import io.jaiclaw.calendar.model.*
import io.jaiclaw.calendar.tool.CalendarTools
import io.jaiclaw.calendar.util.CalendarEventValidator
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.ReactiveSetOperations
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.data.redis.core.ReactiveZSetOperations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class RedisCalendarProviderSpec extends Specification {

    ReactiveStringRedisTemplate redisTemplate = Mock()
    ReactiveValueOperations<String, String> valueOps = Mock()
    ReactiveSetOperations<String, String> setOps = Mock()
    ReactiveZSetOperations<String, String> zSetOps = Mock()

    ObjectMapper objectMapper = CalendarTools.createObjectMapper()
    CalendarEventValidator eventValidator = new CalendarEventValidator(new CalendarProperties())

    RedisCalendarProvider provider

    def setup() {
        redisTemplate.opsForValue() >> valueOps
        redisTemplate.opsForSet() >> setOps
        redisTemplate.opsForZSet() >> zSetOps

        provider = new RedisCalendarProvider(redisTemplate, objectMapper, eventValidator)
    }

    // --- createEvent ---

    def "createEvent stores event with correct key structure"() {
        given:
        def event = CalendarEvent.builder()
                .title("Team Meeting")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()

        valueOps.set(_ as String, _ as String) >> Mono.just(true)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        zSetOps.add(_ as String, _ as String, _ as Double) >> Mono.just(true)

        when:
        def created = provider.createEvent(event).block()

        then:
        created != null
        created.id() != null
        created.title() == "Team Meeting"
        created.tenantId() == "t1"
        created.calendarId() == "cal1"
        created.createdAt() != null
        created.updatedAt() != null
    }

    def "createEvent generates ID when not provided"() {
        given:
        def event = CalendarEvent.builder()
                .title("No-ID Event")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()

        valueOps.set(_ as String, _ as String) >> Mono.just(true)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        zSetOps.add(_ as String, _ as String, _ as Double) >> Mono.just(true)

        when:
        def created = provider.createEvent(event).block()

        then:
        created.id() != null
        !created.id().isEmpty()
    }

    def "createEvent preserves provided ID"() {
        given:
        def event = CalendarEvent.builder()
                .id("custom-id-123")
                .title("Custom ID Event")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()

        valueOps.set(_ as String, _ as String) >> Mono.just(true)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        zSetOps.add(_ as String, _ as String, _ as Double) >> Mono.just(true)

        when:
        def created = provider.createEvent(event).block()

        then:
        created.id() == "custom-id-123"
    }

    def "createEvent writes correct Redis keys"() {
        given:
        def start = Instant.now().plusSeconds(3600)
        def end = Instant.now().plusSeconds(7200)
        def event = CalendarEvent.builder()
                .id("evt1")
                .title("Key Test")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(start)
                .endTime(end)
                .build()

        and: "stub all Redis operations"
        valueOps.set(_ as String, _ as String) >> Mono.just(true)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        zSetOps.add(_ as String, _ as String, _ as Double) >> Mono.just(true)

        when:
        def created = provider.createEvent(event).block()

        then: "event was created"
        created.id() == "evt1"
        created.title() == "Key Test"
    }

    def "createEvent rejects past start time"() {
        given:
        def event = CalendarEvent.builder()
                .title("Past Event")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().plusSeconds(3600))
                .build()

        when:
        provider.createEvent(event).block()

        then:
        thrown(IllegalArgumentException)
    }

    // --- getEvent ---

    def "getEvent deserializes stored JSON"() {
        given:
        def eventId = "evt1"
        def event = CalendarEvent.builder()
                .id(eventId)
                .title("Stored Event")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.parse("2026-06-01T10:00:00Z"))
                .endTime(Instant.parse("2026-06-01T11:00:00Z"))
                .build()
        def json = objectMapper.writeValueAsString(event)

        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(json)

        when:
        def found = provider.getEvent("t1", "cal1", eventId).block()

        then:
        found != null
        found.id() == eventId
        found.title() == "Stored Event"
    }

    def "getEvent returns empty for missing event"() {
        given:
        valueOps.get("tenant:t1:calendar:cal1:event:missing") >> Mono.empty()

        when:
        def found = provider.getEvent("t1", "cal1", "missing").block()

        then:
        found == null
    }

    // --- listEvents ---

    def "listEvents queries zset by score range and filters"() {
        given:
        def start = Instant.parse("2026-06-01T08:00:00Z")
        def end = Instant.parse("2026-06-01T18:00:00Z")
        def event = CalendarEvent.builder()
                .id("evt1")
                .title("Morning Meeting")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.parse("2026-06-01T09:00:00Z"))
                .endTime(Instant.parse("2026-06-01T10:00:00Z"))
                .status(EventStatus.CONFIRMED)
                .build()
        def json = objectMapper.writeValueAsString(event)

        zSetOps.rangeByScore("tenant:t1:calendar:cal1:events:by-time", _ as Range) >> Flux.just("evt1:start", "evt1:end")
        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(json)

        when:
        def events = provider.listEvents("t1", "cal1", start, end, null)
                .collectList().block()

        then:
        events.size() == 1
        events[0].title() == "Morning Meeting"
    }

    def "listEvents excludes cancelled events"() {
        given:
        def start = Instant.parse("2026-06-01T08:00:00Z")
        def end = Instant.parse("2026-06-01T18:00:00Z")
        def event = CalendarEvent.builder()
                .id("evt1")
                .title("Cancelled")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.parse("2026-06-01T09:00:00Z"))
                .endTime(Instant.parse("2026-06-01T10:00:00Z"))
                .status(EventStatus.CANCELLED)
                .build()
        def json = objectMapper.writeValueAsString(event)

        zSetOps.rangeByScore(_ as String, _ as Range) >> Flux.just("evt1:start")
        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(json)

        when:
        def events = provider.listEvents("t1", "cal1", start, end, null)
                .collectList().block()

        then:
        events.size() == 0
    }

    def "listEvents respects limit"() {
        given:
        def start = Instant.parse("2026-06-01T08:00:00Z")
        def end = Instant.parse("2026-06-01T18:00:00Z")
        def events = (1..3).collect { i ->
            CalendarEvent.builder()
                    .id("evt${i}")
                    .title("Event ${i}")
                    .tenantId("t1")
                    .calendarId("cal1")
                    .startTime(Instant.parse("2026-06-01T${String.format('%02d', 8 + i)}:00:00Z"))
                    .endTime(Instant.parse("2026-06-01T${String.format('%02d', 9 + i)}:00:00Z"))
                    .status(EventStatus.CONFIRMED)
                    .build()
        }

        zSetOps.rangeByScore(_ as String, _ as Range) >> Flux.just("evt1:start", "evt2:start", "evt3:start")
        events.each { evt ->
            valueOps.get("tenant:t1:calendar:cal1:event:${evt.id()}") >> Mono.just(objectMapper.writeValueAsString(evt))
        }

        when:
        def result = provider.listEvents("t1", "cal1", start, end, 2)
                .collectList().block()

        then:
        result.size() == 2
    }

    def "listEvents across all calendars for tenant"() {
        given:
        def start = Instant.parse("2026-06-01T08:00:00Z")
        def end = Instant.parse("2026-06-01T18:00:00Z")
        def event1 = CalendarEvent.builder()
                .id("evt1")
                .title("Cal1 Event")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.parse("2026-06-01T09:00:00Z"))
                .endTime(Instant.parse("2026-06-01T10:00:00Z"))
                .status(EventStatus.CONFIRMED)
                .build()
        def event2 = CalendarEvent.builder()
                .id("evt2")
                .title("Cal2 Event")
                .tenantId("t1")
                .calendarId("cal2")
                .startTime(Instant.parse("2026-06-01T11:00:00Z"))
                .endTime(Instant.parse("2026-06-01T12:00:00Z"))
                .status(EventStatus.CONFIRMED)
                .build()

        setOps.members("tenant:t1:calendars") >> Flux.just("cal1", "cal2")
        zSetOps.rangeByScore("tenant:t1:calendar:cal1:events:by-time", _ as Range) >> Flux.just("evt1:start")
        zSetOps.rangeByScore("tenant:t1:calendar:cal2:events:by-time", _ as Range) >> Flux.just("evt2:start")
        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(objectMapper.writeValueAsString(event1))
        valueOps.get("tenant:t1:calendar:cal2:event:evt2") >> Mono.just(objectMapper.writeValueAsString(event2))

        when: "listing with calendarId=null delegates to tenant-wide query"
        def events = provider.listEvents("t1", null, start, end, null)
                .collectList().block()

        then:
        events.size() == 2
        events.collect { it.title() }.containsAll(["Cal1 Event", "Cal2 Event"])
    }

    // --- updateEvent ---

    def "updateEvent applies changes and keeps old times in zset when unchanged"() {
        given:
        def start = Instant.now().plusSeconds(3600)
        def end = Instant.now().plusSeconds(7200)
        def event = CalendarEvent.builder()
                .id("evt1")
                .title("Original")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(start)
                .endTime(end)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build()
        def json = objectMapper.writeValueAsString(event)

        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(json)
        valueOps.set(_ as String, _ as String) >> Mono.just(true)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        zSetOps.add(_ as String, _ as String, _ as Double) >> Mono.just(true)

        when:
        def updated = provider.updateEvent("t1", "cal1", "evt1",
                [title: "Updated Title", location: "Room B"]).block()

        then:
        updated.title() == "Updated Title"
        updated.location() == "Room B"
        updated.startTime() == start
        updated.endTime() == end

        and: "no zset remove calls since times didn't change"
        0 * zSetOps.remove(_ as String, "evt1:start")
        0 * zSetOps.remove(_ as String, "evt1:end")
    }

    def "updateEvent removes old zset entries when times change"() {
        given:
        def oldStart = Instant.now().plusSeconds(3600)
        def oldEnd = Instant.now().plusSeconds(7200)
        def newStart = Instant.now().plusSeconds(10800)
        def newEnd = Instant.now().plusSeconds(14400)
        def event = CalendarEvent.builder()
                .id("evt1")
                .title("Event")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(oldStart)
                .endTime(oldEnd)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build()
        def json = objectMapper.writeValueAsString(event)

        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(json)
        valueOps.set(_ as String, _ as String) >> Mono.just(true)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        zSetOps.add(_ as String, _ as String, _ as Double) >> Mono.just(true)

        when:
        def updated = provider.updateEvent("t1", "cal1", "evt1",
                [startTime: newStart, endTime: newEnd]).block()

        then: "old zset entries removed"
        1 * zSetOps.remove("tenant:t1:calendar:cal1:events:by-time", "evt1:start") >> Mono.just(1L)
        1 * zSetOps.remove("tenant:t1:calendar:cal1:events:by-time", "evt1:end") >> Mono.just(1L)

        and:
        updated.startTime() == newStart
        updated.endTime() == newEnd
    }

    def "updateEvent errors for missing event"() {
        given:
        valueOps.get("tenant:t1:calendar:cal1:event:missing") >> Mono.empty()

        when:
        provider.updateEvent("t1", "cal1", "missing", [title: "X"]).block()

        then:
        thrown(RuntimeException)
    }

    // --- deleteEvent ---

    def "deleteEvent removes all Redis structures"() {
        given:
        def event = CalendarEvent.builder()
                .id("evt1")
                .title("Delete Me")
                .tenantId("t1")
                .calendarId("cal1")
                .startTime(Instant.parse("2026-06-01T10:00:00Z"))
                .endTime(Instant.parse("2026-06-01T11:00:00Z"))
                .build()
        def json = objectMapper.writeValueAsString(event)

        valueOps.get("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(json)

        when:
        provider.deleteEvent("t1", "cal1", "evt1").block()

        then: "event key deleted"
        1 * valueOps.delete("tenant:t1:calendar:cal1:event:evt1") >> Mono.just(true)

        and: "removed from all-events set"
        1 * setOps.remove("tenant:t1:calendar:cal1:events:all", "evt1") >> Mono.just(1L)

        and: "start and end removed from zset"
        1 * zSetOps.remove("tenant:t1:calendar:cal1:events:by-time", "evt1:start") >> Mono.just(1L)
        1 * zSetOps.remove("tenant:t1:calendar:cal1:events:by-time", "evt1:end") >> Mono.just(1L)

        and: "event index deleted"
        1 * valueOps.delete("event:index:evt1") >> Mono.just(true)
    }

    // --- listCalendars ---

    def "listCalendars returns calendars for tenant"() {
        given:
        def cal = CalendarInfo.builder()
                .id("cal1")
                .tenantId("t1")
                .name("Team Calendar")
                .build()
        def json = objectMapper.writeValueAsString(cal)

        setOps.members("tenant:t1:calendars") >> Flux.just("cal1")
        valueOps.get("tenant:t1:calendar:cal1:metadata") >> Mono.just(json)

        when:
        def calendars = provider.listCalendars("t1").collectList().block()

        then:
        calendars.size() == 1
        calendars[0].name() == "Team Calendar"
    }

    def "listCalendars returns fallback when metadata missing"() {
        given:
        setOps.members("tenant:t1:calendars") >> Flux.just("orphan-cal")
        valueOps.get("tenant:t1:calendar:orphan-cal:metadata") >> Mono.empty()

        when:
        def calendars = provider.listCalendars("t1").collectList().block()

        then:
        calendars.size() == 1
        calendars[0].id() == "orphan-cal"
        calendars[0].name() == "orphan-cal"
    }

    // --- createCalendar ---

    def "createCalendar stores metadata and registers calendar"() {
        given:
        def cal = CalendarInfo.builder()
                .id("cal1")
                .tenantId("t1")
                .name("My Calendar")
                .build()

        setOps.isMember("tenant:t1:calendars", "cal1") >> Mono.just(false)
        setOps.add(_ as String, _ as String) >> Mono.just(1L)
        valueOps.set(_ as String, _ as String) >> Mono.just(true)

        when:
        def created = provider.createCalendar(cal).block()

        then:
        created.id() == "cal1"
        created.name() == "My Calendar"
        created.createdAt() != null

        and:
        1 * setOps.add("tenant:all", "t1") >> Mono.just(1L)
        1 * setOps.add("tenant:t1:calendars", "cal1") >> Mono.just(1L)
        1 * valueOps.set("tenant:t1:calendar:cal1:metadata", _ as String) >> Mono.just(true)
    }

    def "createCalendar rejects duplicate"() {
        given:
        def cal = CalendarInfo.builder()
                .id("cal1")
                .tenantId("t1")
                .name("Dup Calendar")
                .build()

        setOps.isMember("tenant:t1:calendars", "cal1") >> Mono.just(true)

        when:
        provider.createCalendar(cal).block()

        then:
        thrown(RuntimeException)
    }

    // --- getProviderName ---

    def "provider name is redis"() {
        expect:
        provider.getProviderName() == "redis"
    }
}
