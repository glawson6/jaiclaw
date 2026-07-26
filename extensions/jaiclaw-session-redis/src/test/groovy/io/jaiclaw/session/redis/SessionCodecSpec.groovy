package io.jaiclaw.session.redis

import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.MediaAttachment
import io.jaiclaw.core.model.Message
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.model.SessionState
import io.jaiclaw.core.model.SystemMessage
import io.jaiclaw.core.model.TokenUsage
import io.jaiclaw.core.model.ToolResultMessage
import io.jaiclaw.core.model.UserMessage
import spock.lang.Specification

import java.time.Instant

/**
 * Unit tests for {@link SessionCodec}. Every Message subtype round-trips
 * cleanly, unknown envelope fields are tolerated on decode, and missing
 * optional fields default sensibly.
 */
class SessionCodecSpec extends Specification {

    SessionCodec codec = new SessionCodec()

    def "UserMessage round-trips with sender, metadata, and media"() {
        given:
        Instant now = Instant.parse("2026-07-20T12:00:00Z")
        byte[] pngBytes = "png-bytes".getBytes()
        UserMessage original = new UserMessage(
                "u1", now, "hello",
                "alice",
                [locale: "en"] as Map,
                [new MediaAttachment("image/png", pngBytes, "flyer.png")])

        when:
        String encoded = codec.encodeMessage(original)
        Message decoded = codec.decodeMessage(encoded)

        then:
        decoded instanceof UserMessage
        UserMessage u = (UserMessage) decoded
        u.id() == "u1"
        u.timestamp() == now
        u.content() == "hello"
        u.senderId() == "alice"
        u.metadata() == [locale: "en"]
        u.media().size() == 1
        u.media()[0].mimeType() == "image/png"
        u.media()[0].filename() == "flyer.png"
        u.media()[0].bytes() == pngBytes
    }

    def "AssistantMessage round-trips with modelId, usage, metadata"() {
        given:
        Instant now = Instant.parse("2026-07-20T12:00:00Z")
        AssistantMessage original = new AssistantMessage(
                "a1", now, "hi there", "claude-opus-4-7",
                new TokenUsage(100, 50, 300, 20),
                [reason: "hello-response"] as Map)

        when:
        String encoded = codec.encodeMessage(original)
        Message decoded = codec.decodeMessage(encoded)

        then:
        decoded instanceof AssistantMessage
        AssistantMessage a = (AssistantMessage) decoded
        a.id() == "a1"
        a.timestamp() == now
        a.content() == "hi there"
        a.modelId() == "claude-opus-4-7"
        a.usage().inputTokens() == 100
        a.usage().outputTokens() == 50
        a.usage().cacheReadTokens() == 300
        a.usage().cacheWriteTokens() == 20
        a.metadata() == [reason: "hello-response"]
    }

    def "SystemMessage round-trips"() {
        given:
        Instant now = Instant.parse("2026-07-20T12:00:00Z")
        SystemMessage original = new SystemMessage("s1", now, "you are a helpful bot", [prio: "high"] as Map)

        when:
        String encoded = codec.encodeMessage(original)
        Message decoded = codec.decodeMessage(encoded)

        then:
        decoded instanceof SystemMessage
        SystemMessage s = (SystemMessage) decoded
        s.id() == "s1"
        s.content() == "you are a helpful bot"
        s.metadata() == [prio: "high"]
    }

    def "ToolResultMessage round-trips with toolCallId + toolName"() {
        given:
        Instant now = Instant.parse("2026-07-20T12:00:00Z")
        ToolResultMessage original = new ToolResultMessage(
                "t1", now, "42", "tc-xyz", "calculator", [op: "add"] as Map)

        when:
        String encoded = codec.encodeMessage(original)
        Message decoded = codec.decodeMessage(encoded)

        then:
        decoded instanceof ToolResultMessage
        ToolResultMessage t = (ToolResultMessage) decoded
        t.id() == "t1"
        t.content() == "42"
        t.toolCallId() == "tc-xyz"
        t.toolName() == "calculator"
        t.metadata() == [op: "add"]
    }

    def "AssistantMessage with null usage encodes/decodes cleanly"() {
        given:
        AssistantMessage m = new AssistantMessage(
                "a2", Instant.now(), "hi", "some-model", null, [:] as Map)

        when:
        Message decoded = codec.decodeMessage(codec.encodeMessage(m))

        then:
        decoded instanceof AssistantMessage
        ((AssistantMessage) decoded).usage() == null
    }

    def "UserMessage with empty media round-trips as empty list"() {
        given:
        UserMessage m = new UserMessage(
                "u2", Instant.now(), "text-only", "bob", [:] as Map, [])

        when:
        UserMessage decoded = (UserMessage) codec.decodeMessage(codec.encodeMessage(m))

        then:
        decoded.media().isEmpty()
    }

    def "Session metadata round-trips (state, timestamps, tenantId)"() {
        given:
        Session s = new Session(
                "sess-id", "chat:tg:acct:peer", "agent-1", "tenant-x",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-20T15:00:00Z"),
                SessionState.IDLE,
                [] as List<Message>)

        when:
        String encoded = codec.encodeSession(s)
        Session decoded = codec.decodeSession(encoded, [] as List<Message>)

        then:
        decoded.id() == "sess-id"
        decoded.sessionKey() == "chat:tg:acct:peer"
        decoded.agentId() == "agent-1"
        decoded.tenantId() == "tenant-x"
        decoded.createdAt() == Instant.parse("2026-07-01T10:00:00Z")
        decoded.lastActiveAt() == Instant.parse("2026-07-20T15:00:00Z")
        decoded.state() == SessionState.IDLE
    }

    def "Session decode carries the supplied message list through"() {
        given:
        Session s = Session.create("id", "sk", "a")
        String encoded = codec.encodeSession(s)
        List<Message> messages = [new SystemMessage("m1", "hi")]

        when:
        Session decoded = codec.decodeSession(encoded, messages)

        then:
        decoded.messages().size() == 1
        decoded.messages()[0].content() == "hi"
    }

    def "decode tolerates unknown fields on the envelope"() {
        given:
        String jsonBody = '{"type":"user","id":"u9","timestamp":"2026-07-20T12:00:00Z",' +
                '"content":"hello","senderId":"alice",' +
                '"__future_field__":"ignored","x_metrics":123}'

        when:
        Message decoded = codec.decodeMessage(jsonBody)

        then:
        decoded instanceof UserMessage
        ((UserMessage) decoded).senderId() == "alice"
    }

    def "decode missing timestamp on message returns null"() {
        given:
        String jsonBody = '{"type":"system","id":"s9","content":"hi"}'

        when:
        Message decoded = codec.decodeMessage(jsonBody)

        then:
        decoded instanceof SystemMessage
        decoded.timestamp() == null
    }

    def "decode missing state on session defaults to ACTIVE"() {
        given:
        String jsonBody = '{"id":"i","sessionKey":"sk","agentId":"a"}'

        when:
        Session decoded = codec.decodeSession(jsonBody, [] as List)

        then:
        decoded.state() == SessionState.ACTIVE
    }

    def "unknown message type throws"() {
        when:
        codec.decodeMessage('{"type":"mystery","id":"x"}')

        then:
        thrown(IllegalStateException)
    }
}
