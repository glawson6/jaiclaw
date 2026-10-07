package io.jaiclaw.channel.discord

import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.gateway.WebhookDispatcher
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.HexFormat

/**
 * Discord Interactions signature verification at the webhook boundary.
 *
 * <p>Before 1.3.0 the adapter verified nothing and answered Discord's PING
 * challenge, so the endpoint looked healthy in the developer portal while
 * accepting forged interactions from anyone who knew the URL. These specs go
 * through the real {@link WebhookDispatcher} so the HTTP-facing path is what is
 * exercised, not just the verifier in isolation.
 */
class DiscordSignatureVerificationSpec extends Specification {

    @Shared KeyPair keyPair
    @Shared String publicKeyHex

    def setupSpec() {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        byte[] encoded = keyPair.public.encoded
        byte[] raw = new byte[32]
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32)
        publicKeyHex = HexFormat.of().formatHex(raw)
    }

    WebhookDispatcher dispatcher = new WebhookDispatcher()
    ChannelMessageHandler handler = Mock(ChannelMessageHandler)

    private DiscordConfig verifyingConfig() {
        new DiscordConfig("bot-token", "app-id", true, false,
                [] as Set, publicKeyHex, true)
    }

    /** A timestamp inside the replay window — Discord signs now, so tests must too. */
    private static String nowTs() {
        String.valueOf(Instant.now().epochSecond)
    }

    private Map<String, String> signedHeaders(String ts, String body) {
        def signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(ts.getBytes(StandardCharsets.UTF_8))
        signer.update(body.getBytes(StandardCharsets.UTF_8))
        ["x-signature-ed25519"  : HexFormat.of().formatHex(signer.sign()),
         "x-signature-timestamp": ts]
    }

    private DiscordAdapter started(DiscordConfig config) {
        def adapter = new DiscordAdapter(config, dispatcher)
        adapter.start(handler)
        adapter
    }

    def "a correctly signed interaction is accepted and dispatched"() {
        given:
        started(verifyingConfig())
        def body = '{"type":2,"id":"i1","guild_id":"g1","channel_id":"c1",' +
                '"data":{"content":"hello"}}'

        when:
        def response = dispatcher.dispatch("discord", body, signedHeaders(nowTs(), body))

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage(_ as ChannelMessage)
    }

    def "an unsigned interaction is rejected with 401 and never dispatched"() {
        given:
        started(verifyingConfig())
        def body = '{"type":2,"id":"i1","data":{"content":"rm -rf /"}}'

        when: "no signature headers at all"
        def response = dispatcher.dispatch("discord", body, [:])

        then:
        response.statusCode.value() == 401

        and: "the forged command never reaches the agent"
        0 * handler.onMessage(_)
    }

    def "a forged signature is rejected"() {
        given:
        started(verifyingConfig())
        def body = '{"type":2,"id":"i1","data":{"content":"rm -rf /"}}'
        def headers = ["x-signature-ed25519"  : "00" * 64,
                       "x-signature-timestamp": nowTs()]

        when:
        def response = dispatcher.dispatch("discord", body, headers)

        then:
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }

    def "a body swapped after signing is rejected"() {
        given: "signed as a harmless PING, delivered as a command"
        started(verifyingConfig())
        def signedBody = '{"type":1}'
        def headers = signedHeaders(nowTs(), signedBody)

        when:
        def response = dispatcher.dispatch("discord",
                '{"type":2,"id":"i1","data":{"content":"rm -rf /"}}', headers)

        then:
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }

    def "the PING challenge is verified too, not answered blind"() {
        given: "an endpoint that answers PING unverified passes Discord setup while being open"
        started(verifyingConfig())

        when: "an unsigned PING"
        def response = dispatcher.dispatch("discord", '{"type":1}', [:])

        then:
        response.statusCode.value() == 401
    }

    def "a correctly signed PING is answered"() {
        given:
        started(verifyingConfig())
        def body = '{"type":1}'

        when:
        def response = dispatcher.dispatch("discord", body, signedHeaders(nowTs(), body))

        then:
        response.statusCode.value() == 200
        response.body.contains('"type"')
    }

    def "a correctly signed but stale interaction is rejected — signatures do not replay forever"() {
        given: "a request signed six minutes ago"
        started(verifyingConfig())
        def body = '{"type":2,"id":"i1","guild_id":"g1","channel_id":"c1",' +
                '"data":{"content":"hello"}}'
        def stale = String.valueOf(Instant.now().epochSecond - 360)

        when:
        def response = dispatcher.dispatch("discord", body, signedHeaders(stale, body))

        then: "the signature is valid, the freshness is not"
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }

    def "a signed interaction from the future is rejected too"() {
        given:
        started(verifyingConfig())
        def body = '{"type":1}'
        def ahead = String.valueOf(Instant.now().epochSecond + 600)

        when:
        def response = dispatcher.dispatch("discord", body, signedHeaders(ahead, body))

        then:
        response.statusCode.value() == 401
    }

    def "the replay window is exactly five minutes, measured against the adapter clock"() {
        given: "a frozen clock, so the boundary is tested without timing luck"
        def frozen = Clock.fixed(Instant.ofEpochSecond(1_900_000_000L), ZoneOffset.UTC)
        def adapter = started(verifyingConfig())
        adapter.setClock(frozen)
        def body = '{"type":1}'

        expect:
        dispatcher.dispatch("discord", body,
                signedHeaders(String.valueOf(1_900_000_000L - 300), body)).statusCode.value() == 200
        dispatcher.dispatch("discord", body,
                signedHeaders(String.valueOf(1_900_000_000L - 301), body)).statusCode.value() == 401
    }

    def "a non-numeric timestamp is rejected"() {
        given:
        started(verifyingConfig())
        def body = '{"type":1}'

        when:
        def response = dispatcher.dispatch("discord", body, signedHeaders("yesterday", body))

        then:
        response.statusCode.value() == 401
    }

    def "header names are matched case-insensitively"() {
        given: "HTTP header casing is not guaranteed by the transport"
        started(verifyingConfig())
        def body = '{"type":1}'
        def signed = signedHeaders(nowTs(), body)

        when:
        def response = dispatcher.dispatch("discord", body,
                ["X-Signature-Ed25519"  : signed["x-signature-ed25519"],
                 "X-Signature-Timestamp": signed["x-signature-timestamp"]])

        then:
        response.statusCode.value() == 200
    }

    def "verification disabled keeps the pre-1.3.0 behaviour"() {
        given: "default config — verification off"
        started(new DiscordConfig("bot-token", "app-id", true))
        def body = '{"type":2,"id":"i1","guild_id":"g1","channel_id":"c1",' +
                '"data":{"content":"hello"}}'

        when: "an unsigned interaction"
        def response = dispatcher.dispatch("discord", body, [:])

        then: "accepted, as before — the adapter warns loudly at startup instead"
        response.statusCode.value() == 200
        1 * handler.onMessage(_ as ChannelMessage)
    }

    def "webhook mode refuses to start when verification is on with no key"() {
        given: "the Slack/Telegram failure mode this check exists to avoid repeating"
        def config = new DiscordConfig("bot-token", "app-id", true, false,
                [] as Set, "", true)
        def adapter = new DiscordAdapter(config, dispatcher)

        when:
        adapter.start(handler)

        then: "fails closed rather than exposing an unverified endpoint"
        def e = thrown(IllegalStateException)
        e.message.contains("public-key")
    }

    def "gateway mode does not require a public key"() {
        given: "gateway mode has no public endpoint to forge requests against"
        def config = new DiscordConfig("bot-token", "app-id", true, true,
                [] as Set, "", true)
        def adapter = new DiscordAdapter(config, dispatcher)

        when:
        adapter.start(handler)

        then:
        noExceptionThrown()
    }

    def "signatureVerifiable distinguishes 'off' from 'on but unusable'"() {
        expect:
        !new DiscordConfig("t", "a", true, false, [] as Set, "", false).signatureVerifiable()
        !new DiscordConfig("t", "a", true, false, [] as Set, "", true).signatureVerifiable()
        !new DiscordConfig("t", "a", true, false, [] as Set, "   ", true).signatureVerifiable()
        new DiscordConfig("t", "a", true, false, [] as Set, publicKeyHex, true).signatureVerifiable()
    }
}
