package io.jaiclaw.agent.ownership

import spock.lang.Specification

class MentionDetectorSpec extends Specification {

    def "extracts single mention"() {
        expect:
        MentionDetector.extractMentions("Hello @assistant how are you") == ["assistant"]
    }

    def "extracts multiple mentions"() {
        expect:
        MentionDetector.extractMentions("@agent-a and @agent-b please help") == ["agent-a", "agent-b"]
    }

    def "returns empty for no mentions"() {
        expect:
        MentionDetector.extractMentions("No mentions here").isEmpty()
        MentionDetector.extractMentions(null).isEmpty()
        MentionDetector.extractMentions("").isEmpty()
    }

    def "isMentioned is case insensitive"() {
        expect:
        MentionDetector.isMentioned("Hello @Assistant", "assistant")
        MentionDetector.isMentioned("Hello @ASSISTANT", "assistant")
        !MentionDetector.isMentioned("Hello @other", "assistant")
    }
}
