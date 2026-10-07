package io.jaiclaw.core.agent

import spock.lang.Specification

/**
 * The parse is a security decision: whatever it reads as consent authorises a
 * tool call. These specs pin the boundary rather than the happy path.
 */
class ApprovalReplyParserSpec extends Specification {

    def "plain affirmatives approve (without a code)"() {
        when:
        def reply = ApprovalReplyParser.parse(text).get()

        then:
        reply.decision() instanceof ToolApprovalDecision.Approved
        reply.isApproved()
        !reply.hasCode()

        where:
        text << ["yes", "YES", "Yes", " yes ", "yes!", "approve", "approved", "confirm", "confirmed"]
    }

    def "plain negatives deny (without a code)"() {
        when:
        def reply = ApprovalReplyParser.parse(text).get()

        then:
        reply.decision() instanceof ToolApprovalDecision.Denied
        !reply.hasCode()

        where:
        text << ["no", "NO", "no.", "deny", "denied", "reject", "rejected",
                 "cancel", "stop", "abort", "refuse", "veto"]
    }

    def "a verdict followed by a code binds the answer to the question"() {
        when:
        def reply = ApprovalReplyParser.parse(text).get()

        then:
        reply.hasCode()
        reply.code() == "K7Q4"
        reply.isApproved() == approved

        where:
        text          | approved
        "yes K7Q4"    | true
        "YES k7q4"    | true
        "approve K7Q4"| true
        "yes  K7Q4"   | true
        "yes K7Q4!"   | true
        "no K7Q4"     | false
        "deny K7Q4"   | false
    }

    def "a code followed by a verdict works too"() {
        expect:
        ApprovalReplyParser.parse("K7Q4 yes").get().code() == "K7Q4"
        ApprovalReplyParser.parse("K7Q4 yes").get().isApproved()
        ApprovalReplyParser.parse("k7q4 no").get().code() == "K7Q4"
        !ApprovalReplyParser.parse("k7q4 no").get().isApproved()
    }

    def "casual acknowledgements are NOT consent"() {
        expect: "in a shared chat an incidental ok must not authorise a tool call"
        ApprovalReplyParser.parse(text).isEmpty()

        where:
        text << ["ok", "okay", "sure", "go", "go ahead", "y", "yep", "yeah", "yup",
                 "do it", "proceed", "allow", "affirmative", "permit", "fine", "k"]
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
                "yes K7Q4 and also the other one",
        ]
    }

    def "a second token that is not a code is not an answer"() {
        expect:
        ApprovalReplyParser.parse(text).isEmpty()

        where:
        text << ["yes please", "yes K7Q", "yes K7Q45", "yes DENY", "yes 1234", "no thanks", "K7Q4 K7Q4"]
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
        text << ["/yes", "/approve", "/no", "/start", "/yes K7Q4"]
    }

    def "trailing punctuation is tolerated, leading is not"() {
        expect:
        ApprovalReplyParser.parse("yes!!").get().isApproved()
        !ApprovalReplyParser.parse("no...").get().isApproved()

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
            ApprovalReplyParser.parse(token).get().decision() instanceof ToolApprovalDecision.Denied
        }
    }

    def "every verdict token is a single word, so the two-token grammar stays unambiguous"() {
        expect:
        (ApprovalReplyParser.approveTokens() + ApprovalReplyParser.denyTokens()).every {
            !it.contains(" ")
        }
    }

    def "a denial carries a reason the model can relay"() {
        when:
        def verdict = ApprovalReplyParser.parse("no").get().decision()

        then:
        verdict instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) verdict).reason().contains("approver")
    }
}
