package io.jaiclaw.learning.review

import io.jaiclaw.learning.proposal.MemoryProposal
import io.jaiclaw.learning.proposal.SkillPatchProposal
import io.jaiclaw.learning.proposal.SkillProposal
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import spock.lang.Specification

class LlmLearningReviewerSpec extends Specification {

    ChatModel chatModel = Mock()

    private static ChatResponse reply(String text) {
        new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())))
    }

    private static ReviewInput input(String transcript = "user: hi\nassistant: hello") {
        new ReviewInput("acme", "assistant", "agent:slack:acme:C1", transcript, [], null)
    }

    private LlmLearningReviewer reviewer(int budget = 12000) {
        new LlmLearningReviewer(chatModel, budget)
    }

    def "parses the three proposal kinds"() {
        given:
        chatModel.call(_ as Prompt) >> reply('''
            {"proposals":[
              {"kind":"memory","summary":"units","heading":"Preferences","content":"prefers metric"},
              {"kind":"skill","summary":"refunds","skillName":"refund-flow",
               "description":"handles refunds","body":"1. look up order"},
              {"kind":"skill_patch","summary":"fix","skillName":"refund-flow",
               "findText":"old","replaceText":"new"}
            ]}''')

        when:
        def outcome = reviewer().review(input())

        then:
        outcome.proposals().size() == 3
        outcome.proposals()[0] instanceof MemoryProposal
        outcome.proposals()[1] instanceof SkillProposal
        outcome.proposals()[2] instanceof SkillPatchProposal
        outcome.proposals()[0].tenantId() == "acme"
        outcome.proposals()[0].originSessionKey() == "agent:slack:acme:C1"
    }

    def "an empty proposal list is a normal outcome"() {
        given:
        chatModel.call(_ as Prompt) >> reply('{"proposals":[]}')

        expect: "most sessions teach nothing — that must not look like a failure"
        reviewer().review(input()).isEmpty()
    }

    def "JSON wrapped in code fences or prose still parses"() {
        given:
        chatModel.call(_ as Prompt) >> reply(wrapped)

        expect:
        reviewer().review(input()).proposals().size() == 1

        where:
        wrapped << [
                '```json\n{"proposals":[{"kind":"memory","content":"x"}]}\n```',
                'Here is my review:\n{"proposals":[{"kind":"memory","content":"x"}]}\nHope that helps.',
                '{"proposals":[{"kind":"memory","content":"x"}]}',
        ]
    }

    def "a malformed or non-JSON reply yields an empty outcome, never an exception"() {
        given:
        chatModel.call(_ as Prompt) >> reply(bad)

        when:
        def outcome = reviewer().review(input())

        then:
        noExceptionThrown()
        outcome.isEmpty()

        where:
        bad << ["not json at all", "{ broken", '{"proposals": "not an array"}', "", "{}"]
    }

    def "a failing model call is a non-event"() {
        given:
        chatModel.call(_ as Prompt) >> { throw new IllegalStateException("provider down") }

        when:
        def outcome = reviewer().review(input())

        then: "the agent keeps working; we retry on the next cadence window"
        noExceptionThrown()
        outcome.isEmpty()
    }

    def "proposals missing required fields are dropped individually"() {
        given:
        chatModel.call(_ as Prompt) >> reply('''
            {"proposals":[
              {"kind":"memory","summary":"no content"},
              {"kind":"skill","skillName":"no-body"},
              {"kind":"skill_patch","skillName":"x","findText":"a"},
              {"kind":"unknown_kind","content":"x"},
              {"kind":"memory","content":"this one is fine"}
            ]}''')

        when:
        def outcome = reviewer().review(input())

        then: "one bad suggestion does not discard the good one alongside it"
        outcome.proposals().size() == 1
        outcome.proposals()[0].content() == "this one is fine"
    }

    def "the proposal count per review is capped"() {
        given:
        def many = (1..20).collect { """{"kind":"memory","content":"item $it"}""" }.join(",")
        chatModel.call(_ as Prompt) >> reply("""{"proposals":[$many]}""")

        expect: "a runaway reply cannot flood the queue"
        reviewer().review(input()).proposals().size() == 5
    }

    def "a blank transcript is not sent to the model at all"() {
        when:
        def outcome = reviewer().review(new ReviewInput("acme", "a", "s", transcript, [], null))

        then:
        outcome.isEmpty()
        0 * chatModel.call(_ as Prompt)

        where:
        transcript << [null, "", "   "]
    }

    def "long transcripts are truncated head and tail"() {
        given:
        def long_ = "A" * 5000 + "MIDDLE" + "B" * 5000

        when:
        def result = LlmLearningReviewer.truncate(long_, 1000)

        then: "the opening states the goal and the ending shows the resolution"
        result.length() < 1200
        result.startsWith("A")
        result.endsWith("B")
        result.contains("truncated")
        !result.contains("MIDDLE")
    }

    def "short transcripts are left alone"() {
        expect:
        LlmLearningReviewer.truncate("short", 1000) == "short"
    }

    def "skill names are sanitised to safe directory segments"() {
        expect:
        LlmLearningReviewer.sanitizeSkillName(raw) == expected

        where:
        raw                       | expected
        "Refund Flow"             | "refund-flow"
        "../../etc/passwd"        | "etc-passwd"
        "Already-Kebab"           | "already-kebab"
        "weird!!!chars###here"    | "weird-chars-here"
        "  -leading-trailing-  "  | "leading-trailing"
        ""                        | "learned-skill"
        null                      | "learned-skill"
    }

    def "extractJson finds the first balanced object and ignores braces inside strings"() {
        expect:
        LlmLearningReviewer.extractJson('prefix {"a":"has } brace"} suffix') == '{"a":"has } brace"}'
        LlmLearningReviewer.extractJson('{"a":{"b":1}}') == '{"a":{"b":1}}'
        LlmLearningReviewer.extractJson("no braces here") == null
        LlmLearningReviewer.extractJson(null) == null
    }
}
