package io.jaiclaw.autoconfigure

import io.jaiclaw.agent.session.InMemorySessionManager
import io.jaiclaw.agent.session.SessionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Pluggable-chat-memory SPI contract — locks the wiring that lets
 * downstream apps replace the in-memory default with a durable backend
 * (Redis, Postgres, JCache).
 *
 * <p>Two regressions this catches:
 * <ol>
 *   <li>{@link SessionManager} reverting from interface to concrete
 *       class — would defeat the SPI extraction.</li>
 *   <li>{@link JaiClawAgentAutoConfiguration#sessionManager} losing its
 *       {@code @ConditionalOnMissingBean(SessionManager.class)} — would
 *       cause a duplicate-bean conflict whenever a user declares their
 *       own {@code @Bean SessionManager}.</li>
 * </ol>
 *
 * <p>Tracked in {@code docs/issues/no-pluggable-chat-memory.md}.
 */
class SessionManagerSpiSpec extends Specification {

    def "SessionManager is an interface, not a concrete class"() {
        expect:
        SessionManager.isInterface()
    }

    def "InMemorySessionManager implements SessionManager"() {
        expect:
        SessionManager.isAssignableFrom(InMemorySessionManager)
    }

    def "JaiClawAgentAutoConfiguration#sessionManager is conditional on missing SessionManager bean"() {
        given:
        Method bean = JaiClawAgentAutoConfiguration.getDeclaredMethod(
                "sessionManager", io.jaiclaw.core.tenant.TenantGuard)

        expect: "the @Bean factory exists and returns the SPI"
        bean.getAnnotation(Bean) != null
        bean.returnType == SessionManager

        and: "@ConditionalOnMissingBean is present and explicitly types the SPI"
        ConditionalOnMissingBean cond = bean.getAnnotation(ConditionalOnMissingBean)
        cond != null
        cond.value().toList().contains(SessionManager)
    }

    def "a custom SessionManager implementation can be wired in place of InMemorySessionManager"() {
        given: "a hand-rolled implementation simulating a downstream app's Redis backend"
        SessionManager custom = new RecordingSessionManager()

        when:
        custom.getOrCreate("k1", "agentX")
        custom.appendMessage("k1", null)
        custom.close("k1")

        then: "all 14 SPI methods are reachable and dispatch to the custom impl"
        ((RecordingSessionManager) custom).calls == ["getOrCreate", "appendMessage", "close"]
    }

    static class RecordingSessionManager implements SessionManager {
        List<String> calls = []

        @Override io.jaiclaw.core.model.Session getOrCreate(String sessionKey, String agentId) {
            calls << "getOrCreate"
            return null
        }
        @Override void appendMessage(String sessionKey, io.jaiclaw.core.model.Message message) {
            calls << "appendMessage"
        }
        @Override Optional<io.jaiclaw.core.model.Session> get(String sessionKey) {
            calls << "get"
            return Optional.empty()
        }
        @Override void replaceMessages(String sessionKey, List<io.jaiclaw.core.model.Message> newMessages) {
            calls << "replaceMessages"
        }
        @Override io.jaiclaw.core.model.Session transitionState(String sessionKey, io.jaiclaw.core.model.SessionState newState) {
            calls << "transitionState"
            return null
        }
        @Override io.jaiclaw.core.model.Session close(String sessionKey) {
            calls << "close"
            return null
        }
        @Override void reset(String sessionKey) { calls << "reset" }
        @Override List<io.jaiclaw.core.model.Session> listSessions() { calls << "listSessions"; return [] }
        @Override List<io.jaiclaw.core.model.Session> listActiveSessions() { calls << "listActiveSessions"; return [] }
        @Override int messageCount(String sessionKey) { calls << "messageCount"; return 0 }
        @Override boolean exists(String sessionKey) { calls << "exists"; return false }
        @Override int sessionCount() { calls << "sessionCount"; return 0 }
        @Override void setTenantGuard(io.jaiclaw.core.tenant.TenantGuard tenantGuard) { calls << "setTenantGuard" }
        @Override void setHookDispatcher(io.jaiclaw.core.agent.AgentHookDispatcher hooks) { calls << "setHookDispatcher" }
    }
}
