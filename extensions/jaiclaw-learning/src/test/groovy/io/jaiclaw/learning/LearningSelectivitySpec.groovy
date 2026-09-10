package io.jaiclaw.learning

import io.jaiclaw.learning.review.LlmLearningReviewer
import io.jaiclaw.learning.review.ReviewInput
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import spock.lang.Specification

import java.time.Duration

class LearningSelectivitySpec extends Specification {

    ChatModel chatModel = Mock()

    private static ChatResponse reply(String text) {
        new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())))
    }

    private static ReviewInput input() {
        new ReviewInput("acme", "assistant", "sess", "user: hi\nassistant: hello", [], null)
    }

    // ── The compatibility guarantee ──────────────────────────────────────────

    def "BALANCED reproduces exactly the values hardcoded before this enum existed"() {
        expect: "an existing deployment must see no behaviour change"
        LearningSelectivity.BALANCED.minTurns() == 4
        LearningSelectivity.BALANCED.minInterval() == Duration.ofMinutes(5)
        LearningSelectivity.BALANCED.maxProposalsPerReview() == 5
    }

    def "the default selectivity is balanced"() {
        expect:
        LearningProperties.defaults().selectivityLevel() == LearningSelectivity.BALANCED
        LearningProperties.defaults().reviewMinTurns() == 4
        LearningProperties.defaults().reviewMinInterval() == Duration.ofMinutes(5)
        LearningProperties.defaults().maxProposalsPerReview() == 5
    }

    // ── Levels ───────────────────────────────────────────────────────────────

    def "each level tightens or loosens every dimension consistently"() {
        expect: "conservative is strictly stricter than balanced, eager strictly looser"
        LearningSelectivity.CONSERVATIVE.minTurns() > LearningSelectivity.BALANCED.minTurns()
        LearningSelectivity.BALANCED.minTurns() > LearningSelectivity.EAGER.minTurns()

        and:
        LearningSelectivity.CONSERVATIVE.minInterval() > LearningSelectivity.BALANCED.minInterval()
        LearningSelectivity.BALANCED.minInterval() > LearningSelectivity.EAGER.minInterval()

        and:
        LearningSelectivity.CONSERVATIVE.maxProposalsPerReview() < LearningSelectivity.BALANCED.maxProposalsPerReview()
        LearningSelectivity.BALANCED.maxProposalsPerReview() < LearningSelectivity.EAGER.maxProposalsPerReview()
    }

    def "every level supplies non-empty prompt guidance"() {
        expect:
        level.promptGuidance() != null
        !level.promptGuidance().isBlank()

        where:
        level << LearningSelectivity.values()
    }

    def "the guidance actually differs between levels"() {
        given:
        def texts = LearningSelectivity.values()*.promptGuidance()

        expect: "otherwise the knob would only change caps, not the model's threshold"
        texts.toUnique().size() == LearningSelectivity.values().length
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    def "parsing is case-insensitive and whitespace-tolerant"() {
        expect:
        LearningSelectivity.parse(raw) == expected

        where:
        raw              | expected
        "conservative"   | LearningSelectivity.CONSERVATIVE
        "CONSERVATIVE"   | LearningSelectivity.CONSERVATIVE
        "  Eager  "      | LearningSelectivity.EAGER
        "balanced"       | LearningSelectivity.BALANCED
    }

    def "an unrecognised value falls back to balanced rather than failing"() {
        expect: "a typo in one deployment's YAML must not stop learning"
        LearningSelectivity.parse(raw) == LearningSelectivity.BALANCED

        where:
        raw << [null, "", "   ", "aggressive", "0.7", "true"]
    }

    // ── Property binding ─────────────────────────────────────────────────────

    def "selectivity supplies the defaults for turns and interval"() {
        when:
        def props = new LearningProperties("propose", level, 0, null, 12000,
                "/tmp/p", "/tmp/s", false, true, Duration.ofDays(30), Duration.ofDays(90))

        then:
        props.reviewMinTurns() == expectedTurns
        props.reviewMinInterval() == expectedInterval

        where:
        level          | expectedTurns | expectedInterval
        "conservative" | 6             | Duration.ofMinutes(15)
        "balanced"     | 4             | Duration.ofMinutes(5)
        "eager"        | 2             | Duration.ofMinutes(1)
    }

    def "an explicit value overrides the selectivity default"() {
        when: "eager, but the operator pins min-turns to 10"
        def props = new LearningProperties("propose", "eager", 10, Duration.ofHours(1), 12000,
                "/tmp/p", "/tmp/s", false, true, Duration.ofDays(30), Duration.ofDays(90))

        then: "a level can be chosen and still tuned in one dimension"
        props.selectivityLevel() == LearningSelectivity.EAGER
        props.reviewMinTurns() == 10
        props.reviewMinInterval() == Duration.ofHours(1)
        props.maxProposalsPerReview() == 8
    }

    def "the stored selectivity string is normalised"() {
        expect:
        new LearningProperties("propose", "  EAGER ", 0, null, 12000, "/p", "/s",
                false, true, Duration.ofDays(30), Duration.ofDays(90)).selectivity() == "eager"
    }

    // ── Reviewer behaviour ───────────────────────────────────────────────────

    def "the reviewer caps proposals at the level's limit"() {
        given:
        def many = (1..20).collect { """{"kind":"memory","content":"item $it"}""" }.join(",")
        chatModel.call(_ as Prompt) >> reply("""{"proposals":[$many]}""")

        when:
        def outcome = new LlmLearningReviewer(chatModel, 12000, level).review(input())

        then:
        outcome.proposals().size() == expected

        where:
        level                             | expected
        LearningSelectivity.CONSERVATIVE  | 2
        LearningSelectivity.BALANCED      | 5
        LearningSelectivity.EAGER         | 8
    }

    def "the level's guidance reaches the prompt"() {
        given:
        def sent = null
        chatModel.call(_ as Prompt) >> { Prompt p ->
            sent = p.contents
            reply('{"proposals":[]}')
        }

        when:
        new LlmLearningReviewer(chatModel, 12000, level).review(input())

        then: "the threshold the model is told about actually changes"
        sent.contains(marker)
        !sent.contains("{{selectivityGuidance}}")

        where:
        level                            | marker
        LearningSelectivity.CONSERVATIVE | "bet money"
        LearningSelectivity.BALANCED     | "correct and common answer"
        LearningSelectivity.EAGER        | "Lean towards proposing"
    }

    def "the two-arg reviewer constructor still behaves as balanced"() {
        given:
        def many = (1..20).collect { """{"kind":"memory","content":"item $it"}""" }.join(",")
        chatModel.call(_ as Prompt) >> reply("""{"proposals":[$many]}""")

        expect: "pre-existing callers are unaffected"
        new LlmLearningReviewer(chatModel, 12000).review(input()).proposals().size() == 5
    }
}
