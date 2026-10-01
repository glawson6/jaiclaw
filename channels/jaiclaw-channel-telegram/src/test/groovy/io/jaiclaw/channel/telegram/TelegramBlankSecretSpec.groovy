package io.jaiclaw.channel.telegram

import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.gateway.WebhookDispatcher
import spock.lang.Specification

/**
 * Verification must not be silently skipped when the secret token is missing.
 *
 * <p>Same defect as Slack: the guard was
 * {@code verifyWebhook() && !webhookSecretToken().isBlank()}, so
 * {@code verify-webhook: true} with no token accepted every request. The
 * {@code security-hardened} profile sets the flag but cannot set the secret, so
 * this was the likely state for anyone who activated that profile and stopped
 * there.
 */
class TelegramBlankSecretSpec extends Specification {

    WebhookDispatcher dispatcher = new WebhookDispatcher()
    ChannelMessageHandler handler = Mock(ChannelMessageHandler)

    private static TelegramConfig config(boolean verify, String secret) {
        new TelegramConfig("bot-token", "https://example.test/hook", true, 30,
                [] as Set, verify, secret, false,
                TelegramPollingStrategyType.NATIVE)
    }

    private void started(TelegramConfig config) {
        new TelegramAdapter(config, dispatcher).start(handler)
    }

    private static String update() {
        '{"update_id":1,"message":{"message_id":1,"from":{"id":5},' +
                '"chat":{"id":5},"text":"hi"}}'
    }

    def "verification enabled with a blank token rejects rather than accepting"() {
        given:
        started(config(true, ""))

        when:
        def response = dispatcher.dispatch("telegram", update(), [:])

        then:
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }

    def "a null token is treated the same as blank"() {
        given:
        started(config(true, null))

        when:
        def response = dispatcher.dispatch("telegram", update(), [:])

        then:
        response.statusCode.value() == 401
    }

    def "a wrong token is still rejected when one is configured"() {
        given:
        started(config(true, "real-token"))

        when:
        def response = dispatcher.dispatch("telegram", update(),
                ["x-telegram-bot-api-secret-token": "guessed"])

        then:
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }

    def "the correct token is accepted"() {
        given:
        started(config(true, "real-token"))

        when:
        def response = dispatcher.dispatch("telegram", update(),
                ["x-telegram-bot-api-secret-token": "real-token"])

        then:
        response.statusCode.value() == 200
    }

    def "verification disabled still accepts unsigned requests"() {
        given: "unchanged for deployments that never enabled it"
        started(config(false, ""))

        when:
        def response = dispatcher.dispatch("telegram", update(), [:])

        then:
        response.statusCode.value() == 200
    }
}
