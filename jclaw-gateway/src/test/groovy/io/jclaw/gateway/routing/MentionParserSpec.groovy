package io.jclaw.gateway.routing

import spock.lang.Specification

class MentionParserSpec extends Specification {

    MentionParser parser = new MentionParser()

    def "extracts Slack mentions"() {
        expect:
        parser.extractMentions("slack", "Hello <@U12345> check this") == ["U12345"] as Set
    }

    def "extracts Discord mentions"() {
        expect:
        parser.extractMentions("discord", "Hey <@123456789>") == ["123456789"] as Set
    }

    def "extracts Telegram command mentions"() {
        expect:
        parser.extractMentions("telegram", "/status@my_bot") == ["my_bot"] as Set
    }

    def "extracts Telegram @ mentions"() {
        when:
        def mentions = parser.extractMentions("telegram", "Hey @mybot what's up")

        then:
        mentions.contains("mybot")
    }

    def "returns empty for no mentions"() {
        expect:
        parser.extractMentions("slack", "Just a regular message").isEmpty()
    }

    def "handles null and empty text"() {
        expect:
        parser.extractMentions("slack", null).isEmpty()
        parser.extractMentions("slack", "").isEmpty()
    }
}
