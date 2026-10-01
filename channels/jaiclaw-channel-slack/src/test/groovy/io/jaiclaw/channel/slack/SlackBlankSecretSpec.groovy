package io.jaiclaw.channel.slack

import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.gateway.WebhookDispatcher
import spock.lang.Specification

/**
 * Verification must not be silently skipped when the secret is missing.
 *
 * <p>Before 1.3.0 the guard was {@code verifySignature() && !signingSecret().isBlank()},
 * so enabling verification without setting a secret accepted every request while
 * reading, in config and in the docs, as protection. An operator who sets
 * {@code verify-signature: true} and forgets the secret had no verification and
 * no indication of it.
 */
class SlackBlankSecretSpec extends Specification {

    WebhookDispatcher dispatcher = new WebhookDispatcher()
    ChannelMessageHandler handler = Mock(ChannelMessageHandler)

    private SlackAdapter started(SlackConfig config) {
        def adapter = new SlackAdapter(config, dispatcher)
        adapter.start(handler)
        adapter
    }

    private static SlackConfig config(boolean verify, String secret) {
        new SlackConfig("xoxb-token", secret, true, null, [] as Set, verify)
    }

    def "verification enabled with a blank secret rejects rather than accepting"() {
        given:
        started(config(true, ""))
        def body = '{"type":"event_callback","event":{"type":"message","text":"hi"}}'

        when: "no signature headers, and no secret to check them against"
        def response = dispatcher.dispatch("slack", body, [:])

        then: "fails closed — an unsatisfiable check is not an absent one"
        response.statusCode.value() == 401

        and:
        0 * handler.onMessage(_)
    }

    def "a null secret is treated the same as blank"() {
        given:
        started(config(true, null))

        when:
        def response = dispatcher.dispatch("slack", '{"type":"url_verification"}', [:])

        then:
        response.statusCode.value() == 401
    }

    def "verification disabled still accepts unsigned requests"() {
        given: "the pre-existing opt-out, unchanged"
        started(config(false, ""))
        def body = '{"type":"url_verification","challenge":"abc"}'

        when:
        def response = dispatcher.dispatch("slack", body, [:])

        then: "not a breaking change for deployments that never enabled it"
        response.statusCode.value() == 200
    }

    def "a bad signature with a configured secret is still rejected"() {
        given: "the pre-existing path keeps working"
        started(config(true, "real-secret"))

        when:
        def response = dispatcher.dispatch("slack", '{"type":"url_verification"}',
                ["x-slack-signature"        : "v0=deadbeef",
                 "x-slack-request-timestamp": String.valueOf(System.currentTimeSeconds())])

        then:
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }
}
