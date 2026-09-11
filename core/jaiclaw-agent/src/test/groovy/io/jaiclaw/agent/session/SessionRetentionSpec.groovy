package io.jaiclaw.agent.session

import io.jaiclaw.core.model.UserMessage
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SessionRetentionSpec extends Specification {

    InMemorySessionManager sessions

    def setup() {
        sessions = new InMemorySessionManager()
    }

    // ── Default behaviour is unchanged ───────────────────────────────────────

    def "the default policy is unlimited — pre-1.2.0 behaviour"() {
        expect:
        sessions.retentionPolicy().isUnlimited()

        when: "many sessions are created with no policy set"
        500.times { sessions.getOrCreate("s-$it", "agent") }

        then: "nothing is evicted; an upgrade must not silently drop sessions"
        sessions.sessionCount() == 500
    }

    def "a null policy restores unlimited"() {
        when:
        sessions.setRetentionPolicy(SessionRetentionPolicy.bounded())
        sessions.setRetentionPolicy(null)

        then:
        sessions.retentionPolicy().isUnlimited()
    }

    // ── Size bound ───────────────────────────────────────────────────────────

    def "a session limit evicts the longest-idle session"() {
        given:
        sessions.setRetentionPolicy(new SessionRetentionPolicy(3, null, 0))

        when: "four sessions are created, each touched in order"
        ["a", "b", "c"].each { key ->
            sessions.getOrCreate(key, "agent")
            sessions.appendMessage(key, new UserMessage("m-$key", "hi", "u"))
            Thread.sleep(5)
        }
        sessions.getOrCreate("d", "agent")

        then: "the oldest-touched is dropped, not the newest"
        sessions.sessionCount() == 3
        !sessions.exists("a")
        sessions.exists("d")
    }

    def "the session being created is never evicted"() {
        given: "a limit of 1"
        sessions.setRetentionPolicy(new SessionRetentionPolicy(1, null, 0))
        sessions.getOrCreate("existing", "agent")

        when:
        sessions.getOrCreate("brand-new", "agent")

        then: "evicting the caller's own session would be an immediate visible failure"
        sessions.exists("brand-new")
        sessions.sessionCount() == 1
    }

    // ── Idle bound ───────────────────────────────────────────────────────────

    def "idle sessions are evicted once past the timeout"() {
        given:
        def start = Instant.parse("2026-09-10T12:00:00Z")
        sessions.setClock(Clock.fixed(start, ZoneOffset.UTC))
        sessions.setRetentionPolicy(new SessionRetentionPolicy(0, Duration.ofHours(24), 0))
        sessions.getOrCreate("stale", "agent")

        when: "25 hours pass and a new session arrives"
        sessions.setClock(Clock.fixed(start.plus(Duration.ofHours(25)), ZoneOffset.UTC))
        sessions.getOrCreate("fresh", "agent")

        then:
        !sessions.exists("stale")
        sessions.exists("fresh")
    }

    def "a session still inside the idle window survives"() {
        given:
        def start = Instant.parse("2026-09-10T12:00:00Z")
        sessions.setClock(Clock.fixed(start, ZoneOffset.UTC))
        sessions.setRetentionPolicy(new SessionRetentionPolicy(0, Duration.ofHours(24), 0))
        sessions.getOrCreate("recent", "agent")

        when:
        sessions.setClock(Clock.fixed(start.plus(Duration.ofHours(1)), ZoneOffset.UTC))
        sessions.getOrCreate("other", "agent")

        then:
        sessions.exists("recent")
    }

    def "activity resets the idle clock"() {
        given:
        def start = Instant.parse("2026-09-10T12:00:00Z")
        sessions.setClock(Clock.fixed(start, ZoneOffset.UTC))
        sessions.setRetentionPolicy(new SessionRetentionPolicy(0, Duration.ofHours(24), 0))
        sessions.getOrCreate("busy", "agent")

        when: "touched at hour 20, then checked at hour 25"
        sessions.setClock(Clock.fixed(start.plus(Duration.ofHours(20)), ZoneOffset.UTC))
        sessions.appendMessage("busy", new UserMessage("m", "still here", "u"))
        sessions.setClock(Clock.fixed(start.plus(Duration.ofHours(25)), ZoneOffset.UTC))
        sessions.getOrCreate("trigger", "agent")

        then: "an active conversation is not dropped just because it started long ago"
        sessions.exists("busy")
    }

    // ── Per-session message cap ──────────────────────────────────────────────

    def "a message cap drops the oldest turns"() {
        given:
        sessions.setRetentionPolicy(new SessionRetentionPolicy(0, null, 3))
        sessions.getOrCreate("chatty", "agent")

        when:
        5.times { sessions.appendMessage("chatty", new UserMessage("m$it", "msg $it", "u")) }

        then: "one runaway conversation cannot exhaust heap on its own"
        def messages = sessions.get("chatty").get().messages()
        messages.size() == 3
        messages*.content() == ["msg 2", "msg 3", "msg 4"]
    }

    def "no cap means no truncation"() {
        given:
        sessions.getOrCreate("unbounded", "agent")

        when:
        50.times { sessions.appendMessage("unbounded", new UserMessage("m$it", "msg $it", "u")) }

        then:
        sessions.get("unbounded").get().messages().size() == 50
    }

    // ── Policy record ────────────────────────────────────────────────────────

    def "non-positive bounds are normalised to unlimited"() {
        when:
        def p = new SessionRetentionPolicy(limit, idle, cap)

        then:
        p.hasSessionLimit() == expectLimit
        p.hasIdleTimeout() == expectIdle
        p.hasMessageLimit() == expectCap

        where:
        limit | idle                    | cap | expectLimit | expectIdle | expectCap
        0     | null                    | 0   | false       | false      | false
        -5    | Duration.ZERO           | -1  | false       | false      | false
        10    | Duration.ofHours(1)     | 100 | true        | true       | true
    }

    def "the bounded preset is a usable gateway default"() {
        when:
        def p = SessionRetentionPolicy.bounded()

        then:
        p.maxSessions() == 10_000
        p.idleTimeout() == Duration.ofHours(24)
        p.maxMessagesPerSession() == 500
        !p.isUnlimited()
    }

    def "eviction fires SessionEndedEvent so listeners can clean up"() {
        given:
        def hooks = Mock(io.jaiclaw.core.agent.AgentHookDispatcher)
        def mgr = new InMemorySessionManager(null, hooks)
        mgr.setRetentionPolicy(new SessionRetentionPolicy(1, null, 0))
        mgr.getOrCreate("first", "agent")

        when:
        mgr.getOrCreate("second", "agent")

        then:
        1 * hooks.fireVoid({ it instanceof io.jaiclaw.core.hook.event.SessionEndedEvent
                && it.sessionKey() == "first" })
    }
}
