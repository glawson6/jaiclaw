package io.jaiclaw.agentmind.tendencies.learning

import io.jaiclaw.core.model.Tendencies
import spock.lang.Specification

import java.util.List
import java.util.Optional

class DeterministicTendenciesProviderSpec extends Specification {

    DeterministicTendenciesProvider provider = new DeterministicTendenciesProvider()

    Tendencies emptyFor(String userId) {
        Tendencies.forUser("acme", userId, "", [:])
    }

    def "type identifier is 'deterministic'"() {
        expect:
        provider.type() == "deterministic"
    }

    // ---------- thresholds ----------

    def "transcript below MIN_MESSAGES_FOR_INFERENCE returns empty"() {
        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), ["hi", "hello"])

        then:
        result.empty
    }

    def "null transcript returns empty"() {
        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), null)

        then:
        result.empty
    }

    // ---------- prefers_brevity ----------

    def "consistently short messages produce prefers_brevity=true"() {
        given:
        List<String> transcript = ["ok", "thanks", "yes", "sure", "got it"]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        result.get().traits()["prefers_brevity"] == "true"
    }

    def "consistently long messages produce prefers_detail=true"() {
        given:
        String longMsg = "x" * 400
        List<String> transcript = [longMsg, longMsg, longMsg, longMsg]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        result.get().traits()["prefers_detail"] == "true"
    }

    // ---------- tech_leaning ----------

    def "many technical-keyword messages produce tech_leaning=high"() {
        given:
        List<String> transcript = [
                "I'm seeing an exception in the API logs.",
                "Can you check the kubernetes deployment?",
                "The build error mentions a regex match issue.",
                "Look at this stack trace from the docker container."
        ]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        result.get().traits()["tech_leaning"] == "high"
    }

    def "no technical keywords means tech_leaning is absent"() {
        given:
        List<String> transcript = [
                "How is your day going?",
                "I wanted to ask about the weather.",
                "Do you like coffee or tea?"
        ]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        // brevity/detail thresholds aren't tripped here — message lengths are mid-range
        !result.present || !result.get().traits().containsKey("tech_leaning")
    }

    // ---------- bullets + examples ----------

    def "messages with bullet markers produce prefers_bullets=true"() {
        given:
        List<String> transcript = [
                "- first thing\n- second thing",
                "1. step one\n2. step two",
                "Some text",
                "* a\n* b\n* c"
        ]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        result.get().traits()["prefers_bullets"] == "true"
    }

    def "messages asking for examples produce prefers_examples=true"() {
        given:
        List<String> transcript = [
                "Can you show me how this works?",
                "Give me an example please.",
                "Walk through this code with me.",
                "Sample data would help."
        ]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        result.get().traits()["prefers_examples"] == "true"
    }

    // ---------- question rate ----------

    def "high question rate produces question_rate=high"() {
        given:
        List<String> transcript = [
                "What does this do?",
                "Why is it slow?",
                "How can I fix this?",
                "Is there a better way?",
                "Statement without a question."
        ]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        result.get().traits()["question_rate"] == "high"
    }

    // ---------- output shape ----------

    def "produced PeerCard renders observed traits as bullet list"() {
        given:
        List<String> transcript = ["ok", "thanks", "yes", "got it", "sure"]

        when:
        Optional<Tendencies> result = provider.learn(emptyFor("u1"), transcript)

        then:
        result.present
        String md = result.get().peerCardMarkdown()
        md.startsWith("# Observed Tendencies")
        md.contains("- prefers_brevity: true")
    }

    def "withDialecticResult is applied — version + dialecticPasses bumped"() {
        given:
        Tendencies current = new Tendencies(io.jaiclaw.core.model.TendenciesScope.USER,
                "acme", "u-1", "", [:],
                java.time.Instant.now(), null, 4L, 7L)
        List<String> transcript = ["short", "msg", "again", "ok"]

        when:
        Optional<Tendencies> result = provider.learn(current, transcript)

        then:
        result.present
        result.get().version() == 8L
        result.get().dialecticPasses() == 5L
        result.get().lastDialecticAt() != null
    }

    // ---------- no-change detection ----------

    def "same traits as current returns empty (no observable change)"() {
        given:
        Map<String, String> traits = [prefers_brevity: "true"]
        Tendencies current = Tendencies.forUser("acme", "u-1",
                DeterministicTendenciesProvider.renderPeerCard(traits), traits)
        List<String> transcript = ["ok", "thanks", "yes", "got it", "sure"]

        when:
        Optional<Tendencies> result = provider.learn(current, transcript)

        then:
        result.empty
    }
}
