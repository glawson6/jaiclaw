package io.jaiclaw.core.agent

import spock.lang.Specification

/**
 * The parse is a security decision: whatever it reads as consent authorises a
 * tool call. These specs pin the boundary rather than the happy path.
 */
class ApprovalReplyParserSpec extends Specification {

    def "plain affirmatives approve"() {
        expect:
        ApprovalReplyParser.parse(text).get() instanceof ToolApprovalDecision.Approved

        where:
        text << ["yes", "y", "YES", "Yes", " yes ", "yes!", "ok", "okay",
                 "approve", "approved", "confirm", "confirmed", "go ahead",
                 "do it", "proceed", "sure", "yep", "allow"]
    }

    def "plain negatives deny"() {
        expect:
        ApprovalReplyParser.parse(text).get() instanceof ToolApprovalDecision.Denied

        where:
        text << ["no", "n", "NO", "no.", "nope", "deny", "denied", "reject",
                 "cancel", "stop", "abort", "don't", "dont", "do not", "refuse"]
    }

    def "a sentence containing an affirmative is NOT consent"() {
        expect: "substring matching would read refusals as approvals"
        ApprovalReplyParser.parse(text).isEmpty()

        where:
        text << [
                "don't approve that",
                "do not approve",
                "why would I approve that?",
                "approve nothing",
                "I would say yes but not to this",
                "yes to the other one",
                "is that a yes or a no",
                "ok so what does it do",
        ]
    }

    def "an unrecognised reply is not a denial — it is not an answer"() {
        when: "the approver asks a question instead of answering"
        def verdict = ApprovalReplyParser.parse(text)

        then: "empty, so the caller can leave the request pending and let the message through"
        verdict.isEmpty()
        !ApprovalReplyParser.isAnswer(text)

        where:
        text << ["what does that do?", "hold on", "hmm", "explain first",
                 "who is asking", "later", ""]
    }

    def "null and blank are not answers"() {
        expect:
        ApprovalReplyParser.parse(text).isEmpty()

        where:
        text << [null, "", "   ", "\n", "\t "]
    }

    def "a slash command is not an answer"() {
        expect: "leading punctuation means a command invocation, not consent"
        ApprovalReplyParser.parse(text).isEmpty()

        where:
        text << ["/yes", "/approve", "/no", "/start"]
    }

    def "internal whitespace is collapsed"() {
        expect:
        ApprovalReplyParser.parse("go  ahead").get() instanceof ToolApprovalDecision.Approved
        ApprovalReplyParser.parse("do   not").get() instanceof ToolApprovalDecision.Denied
    }

    def "trailing punctuation is tolerated, leading is not"() {
        expect:
        ApprovalReplyParser.parse("yes!!").get() instanceof ToolApprovalDecision.Approved
        ApprovalReplyParser.parse("no...").get() instanceof ToolApprovalDecision.Denied

        and:
        ApprovalReplyParser.parse("!yes").isEmpty()
    }

    def "the vocabularies do not overlap"() {
        when: "an overlap would make the verdict depend on check order"
        def both = ApprovalReplyParser.approveTokens().intersect(
                ApprovalReplyParser.denyTokens())

        then:
        both.isEmpty()
    }

    def "denial wins if the vocabularies ever do overlap"() {
        expect: "deny is checked first, so the safer reading survives a future edit"
        ApprovalReplyParser.denyTokens().every { token ->
            ApprovalReplyParser.parse(token).get() instanceof ToolApprovalDecision.Denied
        }
    }

    def "a denial carries a reason the model can relay"() {
        when:
        def verdict = ApprovalReplyParser.parse("no").get()

        then:
        verdict instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) verdict).reason().contains("approver")
    }
}
