package io.jaiclaw.session.redis

import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.HookEvent
import io.jaiclaw.core.hook.event.SessionEndedEvent
import io.jaiclaw.core.hook.event.SessionStartedEvent
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.model.SessionState
import io.jaiclaw.core.model.SystemMessage
import io.jaiclaw.core.model.UserMessage
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

/**
 * Behavioural tests for {@link RedisSessionManager} against a mocked
 * {@link StringRedisTemplate}. Deliberately mock-heavy — the manager is
 * thin enough that verifying the Redis operation contract is sufficient
 * for the SPI. An integration spec against a real Redis (Testcontainers)
 * is deferred; see the plan's "Deferred" section.
 */
class RedisSessionManagerSpec extends Specification {

    StringRedisTemplate template = Mock()
    ValueOperations<String, String> value = Mock()
    ListOperations<String, String> list = Mock()
    SetOperations<String, String> setOps = Mock()
    TenantGuard tenantGuard = new TenantGuard(TenantProperties.DEFAULT)  // SINGLE mode
    AgentHookDispatcher hooks = Mock()

    def setup() {
        template.opsForValue() >> value
        template.opsForList() >> list
        template.opsForSet() >> setOps
    }

    def newManager() {
        RedisSessionManager m = new RedisSessionManager(
                template, tenantGuard, "jaiclaw:sessions", Duration.ofHours(1))
        m.setHookDispatcher(hooks)
        return m
    }

    def "getOrCreate on a fresh key SET NX + SADD + fires SessionStartedEvent"() {
        given:
        RedisSessionManager m = newManager()

        when:
        Session s = m.getOrCreate("chat:tg:acct:peer", "agent-1")

        then:
        1 * value.get("jaiclaw:sessions::chat:tg:acct:peer") >> null
        1 * value.setIfAbsent("jaiclaw:sessions::chat:tg:acct:peer", _ as String, Duration.ofHours(1)) >> true
        1 * setOps.add("jaiclaw:sessions::idx:sessions", "chat:tg:acct:peer") >> 1L
        1 * hooks.fireVoid({ HookEvent e -> e instanceof SessionStartedEvent })
        s.agentId() == "agent-1"
        s.state() == SessionState.ACTIVE
    }

    def "getOrCreate on an existing key returns decoded session without fire"() {
        given:
        RedisSessionManager m = newManager()
        SessionCodec codec = new SessionCodec()
        Session existing = new Session("id", "sk", "agent-1", null,
                Instant.now(), Instant.now(), SessionState.ACTIVE, [] as List)
        String encoded = codec.encodeSession(existing)

        when:
        Session s = m.getOrCreate("sk", "agent-1")

        then:
        1 * value.get("jaiclaw:sessions::sk") >> encoded
        1 * list.range("jaiclaw:sessions::sk:msgs", 0L, -1L) >> []
        0 * hooks.fireVoid(_)
        s.id() == "id"
    }

    def "appendMessage no-ops when metadata key does not exist"() {
        given:
        RedisSessionManager m = newManager()

        when:
        m.appendMessage("missing", new SystemMessage("m1", "hi"))

        then:
        1 * template.hasKey("jaiclaw:sessions::missing") >> false
        0 * list.rightPush(_, _)
    }

    def "appendMessage RPUSHes + refreshes both TTLs when session exists"() {
        given:
        RedisSessionManager m = newManager()
        SessionCodec codec = new SessionCodec()
        String encoded = codec.encodeSession(Session.create("id", "sk", "a"))

        when:
        m.appendMessage("sk", new SystemMessage("m1", "hi"))

        then:
        1 * template.hasKey("jaiclaw:sessions::sk") >> true
        1 * list.rightPush("jaiclaw:sessions::sk:msgs", _ as String)
        1 * template.expire("jaiclaw:sessions::sk:msgs", Duration.ofHours(1)) >> true
        1 * value.get("jaiclaw:sessions::sk") >> encoded
        1 * value.set("jaiclaw:sessions::sk", _ as String, Duration.ofHours(1))
    }

    def "get returns empty when metadata key missing"() {
        given:
        RedisSessionManager m = newManager()

        when:
        Optional<Session> result = m.get("missing")

        then:
        1 * value.get("jaiclaw:sessions::missing") >> null
        !result.isPresent()
    }

    def "get returns session with messages loaded from LRANGE"() {
        given:
        RedisSessionManager m = newManager()
        SessionCodec codec = new SessionCodec()
        Session base = Session.create("id", "sk", "a")
        String encoded = codec.encodeSession(base)
        String msgJson = codec.encodeMessage(new SystemMessage("m1", "hi"))

        when:
        Optional<Session> result = m.get("sk")

        then:
        1 * value.get("jaiclaw:sessions::sk") >> encoded
        1 * list.range("jaiclaw:sessions::sk:msgs", 0L, -1L) >> [msgJson]
        result.isPresent()
        result.get().messages().size() == 1
        result.get().messages()[0].content() == "hi"
    }

    def "reset deletes both keys + SREM from index + fires SessionEndedEvent"() {
        given:
        RedisSessionManager m = newManager()
        SessionCodec codec = new SessionCodec()
        String encoded = codec.encodeSession(Session.create("id", "sk", "agent-1"))

        when:
        m.reset("sk")

        then:
        1 * value.get("jaiclaw:sessions::sk") >> encoded
        1 * template.execute(_) >> [1L, 1L, 1L]      // three MULTI ops
        1 * hooks.fireVoid({ HookEvent e ->
            e instanceof SessionEndedEvent && ((SessionEndedEvent) e).reason() == "reset"
        })
    }

    def "reset with no existing session does not fire SessionEndedEvent"() {
        given:
        RedisSessionManager m = newManager()

        when:
        m.reset("missing")

        then:
        1 * value.get("jaiclaw:sessions::missing") >> null
        1 * template.execute(_) >> [0L, 0L, 0L]
        0 * hooks.fireVoid({ it instanceof SessionEndedEvent })
    }

    def "messageCount returns LLEN"() {
        given:
        RedisSessionManager m = newManager()

        when:
        int count = m.messageCount("sk")

        then:
        1 * list.size("jaiclaw:sessions::sk:msgs") >> 7L
        count == 7
    }

    def "messageCount returns 0 when LLEN is null"() {
        given:
        RedisSessionManager m = newManager()

        when:
        int count = m.messageCount("sk")

        then:
        1 * list.size(_) >> null
        count == 0
    }

    def "exists returns EXISTS on metadata key"() {
        given:
        RedisSessionManager m = newManager()

        when:
        boolean present = m.exists("sk")

        then:
        1 * template.hasKey("jaiclaw:sessions::sk") >> true
        present
    }

    def "sessionCount returns SCARD of the index"() {
        given:
        RedisSessionManager m = newManager()

        when:
        int count = m.sessionCount()

        then:
        1 * setOps.size("jaiclaw:sessions::idx:sessions") >> 3L
        count == 3
    }

    def "listSessions prunes stale index entries"() {
        given:
        RedisSessionManager m = newManager()
        SessionCodec codec = new SessionCodec()
        String liveEncoded = codec.encodeSession(Session.create("id", "live", "a"))

        when:
        List<Session> sessions = m.listSessions()

        then:
        1 * setOps.members("jaiclaw:sessions::idx:sessions") >> (["live", "stale"] as Set)
        1 * value.get("jaiclaw:sessions::live") >> liveEncoded
        1 * value.get("jaiclaw:sessions::stale") >> null
        1 * setOps.remove("jaiclaw:sessions::idx:sessions", { Object[] args -> args[0] == "stale" })
        sessions.size() == 1
        sessions[0].sessionKey() == "live"
    }

    def "constructor default falls back to safe values for null args"() {
        when:
        RedisSessionManager m = new RedisSessionManager(template, null, null, null)

        then:
        m != null
        noExceptionThrown()
    }

    def "constructor rejects null template"() {
        when:
        new RedisSessionManager(null)

        then:
        thrown(IllegalArgumentException)
    }
}
