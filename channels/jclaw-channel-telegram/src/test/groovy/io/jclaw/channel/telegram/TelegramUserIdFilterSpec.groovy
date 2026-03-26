package io.jclaw.channel.telegram

import io.jclaw.channel.ChannelMessage
import io.jclaw.channel.ChannelMessageHandler
import io.jclaw.security.ratelimit.UserRateLimiter
import spock.lang.Specification
import spock.lang.Subject

class TelegramUserIdFilterSpec extends Specification {

    def rateLimiter = new UserRateLimiter(100)
    def downstream = Mock(ChannelMessageHandler)

    @Subject
    def filter = new TelegramUserIdFilter(["12345", "67890"] as Set, rateLimiter)

    def setup() {
        filter.setDownstream(downstream)
    }

    def "should pass through messages from allowed Telegram users"() {
        given:
        def message = ChannelMessage.inbound("1", "telegram", "bot-token", "12345", "hello", null)

        when:
        filter.onMessage(message)

        then:
        1 * downstream.onMessage(message)
    }

    def "should block messages from unauthorized Telegram users"() {
        given:
        def message = ChannelMessage.inbound("1", "telegram", "bot-token", "99999", "hello", null)

        when:
        filter.onMessage(message)

        then:
        0 * downstream.onMessage(_)
    }

    def "should pass through non-Telegram messages regardless of user ID"() {
        given:
        def message = ChannelMessage.inbound("1", "slack", "account", "any-user", "hello", null)

        when:
        filter.onMessage(message)

        then:
        1 * downstream.onMessage(message)
    }

    def "should allow all users when allowedUserIds is empty"() {
        given:
        def openFilter = new TelegramUserIdFilter([] as Set, rateLimiter)
        openFilter.setDownstream(downstream)
        def message = ChannelMessage.inbound("1", "telegram", "bot-token", "99999", "hello", null)

        when:
        openFilter.onMessage(message)

        then:
        1 * downstream.onMessage(message)
    }

    def "should block rate-limited users even if authorized"() {
        given:
        def strictLimiter = new UserRateLimiter(2)
        def limitedFilter = new TelegramUserIdFilter(["12345"] as Set, strictLimiter)
        limitedFilter.setDownstream(downstream)
        def message = ChannelMessage.inbound("1", "telegram", "bot-token", "12345", "hello", null)

        when: "send 3 messages (limit is 2)"
        limitedFilter.onMessage(message)
        limitedFilter.onMessage(message)
        limitedFilter.onMessage(message)

        then: "only 2 pass through"
        2 * downstream.onMessage(message)
    }
}
