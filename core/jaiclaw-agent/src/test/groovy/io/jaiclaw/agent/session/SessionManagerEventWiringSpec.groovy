package io.jaiclaw.agent.session

import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.HookEvent
import io.jaiclaw.core.hook.event.SessionEndedEvent
import io.jaiclaw.core.hook.event.SessionStartedEvent
import spock.lang.Specification

/**
 * Verifies that {@link SessionManager} fires {@link SessionStartedEvent} and
 * {@link SessionEndedEvent} through the injected {@link AgentHookDispatcher}.
 *
 * <p>Plan §5 task 1.1 — the per-user persistent-state work depends on these events
 * being live in 0.8.x. Specs catch regressions if a future refactor of
 * SessionManager forgets to dispatch.
 */
class SessionManagerEventWiringSpec extends Specification {

    AgentHookDispatcher hooks = Mock()
    SessionManager manager = new InMemorySessionManager(null, hooks)

    def "getOrCreate fires SessionStartedEvent exactly once for a new session"() {
        when:
        manager.getOrCreate("agentX:slack:acct:peer", "agentX")

        then:
        1 * hooks.fireVoid({ HookEvent e ->
            e instanceof SessionStartedEvent &&
                e.agentId() == "agentX" &&
                e.sessionKey() == "agentX:slack:acct:peer"
        })
        0 * hooks.fireVoid(_ as SessionEndedEvent)
    }

    def "getOrCreate does NOT re-fire SessionStartedEvent on the second call"() {
        when:
        manager.getOrCreate("k1", "agentX")
        manager.getOrCreate("k1", "agentX")

        then:
        1 * hooks.fireVoid(_ as SessionStartedEvent)
        0 * hooks.fireVoid(_)
    }

    def "close fires SessionEndedEvent with reason=closed"() {
        when:
        manager.getOrCreate("k1", "agentX")
        manager.close("k1")

        then:
        1 * hooks.fireVoid(_ as SessionStartedEvent)
        1 * hooks.fireVoid({ SessionEndedEvent e ->
            e.agentId() == "agentX" &&
                e.sessionKey() == "k1" &&
                e.reason() == "closed"
        })
    }

    def "reset fires SessionEndedEvent with reason=reset and removes the session"() {
        when:
        manager.getOrCreate("k1", "agentX")
        manager.reset("k1")

        then:
        1 * hooks.fireVoid(_ as SessionStartedEvent)
        1 * hooks.fireVoid({ SessionEndedEvent e ->
            e.agentId() == "agentX" &&
                e.sessionKey() == "k1" &&
                e.reason() == "reset"
        })
        !manager.exists("k1")
    }

    def "close on a non-existent session fires no SessionEndedEvent"() {
        when:
        manager.close("nope")

        then:
        0 * hooks.fireVoid(_)
    }

    def "reset on a non-existent session fires no SessionEndedEvent"() {
        when:
        manager.reset("nope")

        then:
        0 * hooks.fireVoid(_)
    }

    def "no dispatcher means no fire, no exception"() {
        given:
        SessionManager noHooks = new InMemorySessionManager()

        when:
        noHooks.getOrCreate("k1", "agentX")
        noHooks.close("k1")
        noHooks.reset("k1")

        then:
        notThrown(Exception)
    }

    def "dispatcher throwing does not break session lifecycle"() {
        given:
        AgentHookDispatcher boom = Mock()
        boom.fireVoid(_) >> { throw new RuntimeException("hook plugin crashed") }
        SessionManager m = new InMemorySessionManager(null, boom)

        when:
        def s = m.getOrCreate("k1", "agentX")

        then:
        s != null
        s.sessionKey() == "k1"
    }

    def "setHookDispatcher allows late injection"() {
        given:
        SessionManager m = new InMemorySessionManager()

        when:
        m.setHookDispatcher(hooks)
        m.getOrCreate("k1", "agentX")

        then:
        1 * hooks.fireVoid(_ as SessionStartedEvent)
    }
}
