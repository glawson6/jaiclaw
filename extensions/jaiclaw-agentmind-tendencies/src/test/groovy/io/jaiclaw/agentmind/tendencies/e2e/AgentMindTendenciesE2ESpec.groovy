package io.jaiclaw.agentmind.tendencies.e2e

import io.jaiclaw.agentmind.tendencies.AgentMindTendenciesProperties
import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate
import io.jaiclaw.agentmind.tendencies.cost.TendenciesTokenBudget
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor
import io.jaiclaw.agentmind.tendencies.hook.TendenciesDialecticTrigger
import io.jaiclaw.agentmind.tendencies.hook.TendenciesUserMessageInjector
import io.jaiclaw.agentmind.tendencies.transcript.InMemoryTranscriptSource
import io.jaiclaw.agentmind.tendencies.transcript.TranscriptSource
import io.jaiclaw.core.agent.TendenciesStoreProvider
import io.jaiclaw.core.hook.event.MessageReceivedEvent
import io.jaiclaw.core.hook.event.SessionEndedEvent
import io.jaiclaw.core.model.Tendencies
import io.jaiclaw.core.model.TendenciesScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import spock.lang.Specification

import java.nio.file.Files
import java.util.Optional

/**
 * Plan §8 task 3.16 — E2E spec.
 *
 * Boots the full Spring context with the Tendencies pillar enabled, then
 * drives a realistic scenario against the JSON backend:
 *
 * <ol>
 *   <li>Records 5 messages into the in-memory transcript source via
 *       MessageReceivedEvent (directly, no real channel adapter
 *       involved).</li>
 *   <li>Fires SessionEndedEvent through the dialectic trigger.</li>
 *   <li>Waits for the StripedDialecticExecutor to drain.</li>
 *   <li>Asserts a Tendencies record was persisted for the user; the
 *       deterministic provider's traits match the transcript's
 *       brevity / question signal.</li>
 *   <li>Calls the user-message injector on a subsequent
 *       MessageReceivedEvent and asserts the resulting message body
 *       starts with a <tendencies-context> block.</li>
 * </ol>
 */
@SpringBootTest(classes = AgentMindTendenciesE2ESpec.TestApp, properties = [
        "jaiclaw.agentmind.tendencies.enabled=true",
        "jaiclaw.agentmind.tendencies.cadence.min-interval=PT0S",
        "jaiclaw.agentmind.tendencies.cadence.min-turns=3",
        "jaiclaw.agentmind.tendencies.root-dir=\${java.io.tmpdir}/agentmind-tendencies-e2e"
])
class AgentMindTendenciesE2ESpec extends Specification {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {}

    @Autowired TendenciesStoreProvider store
    @Autowired TendenciesCadenceGate cadenceGate
    @Autowired StripedDialecticExecutor executor
    @Autowired TendenciesUserMessageInjector userMessageInjector
    @Autowired TendenciesDialecticTrigger dialecticTrigger
    @Autowired TranscriptSource transcript
    @Autowired AgentMindTendenciesProperties props

    String channel = "slack"
    String peer = "user-e2e"
    String userKey = TendenciesUserMessageInjector.userKeyFor(channel, peer)
    String sessionKey = "agent:" + channel + ":acct:" + peer

    def setup() {
        // Reset root dir between specs.
        def root = java.nio.file.Path.of(props.rootDir())
        if (Files.exists(root)) {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files.&deleteIfExists)
        }
        // Reset in-memory transcript so a previous spec's messages don't
        // bleed into this one's threshold counting.
        ((InMemoryTranscriptSource) transcript).clear(sessionKey)
    }

    def "context wires all major beans"() {
        expect:
        store != null
        cadenceGate != null
        executor != null
        userMessageInjector != null
        dialecticTrigger != null
        transcript instanceof InMemoryTranscriptSource
    }

    def "5 messages + SessionEnded → exactly one dialectic pass writes a Tendencies record"() {
        given: "5 short messages recorded in the in-memory transcript"
        InMemoryTranscriptSource src = (InMemoryTranscriptSource) transcript
        ["ok?", "thanks?", "yes?", "sure?", "got it?"].each { msg ->
            src.record(sessionKey, msg)
        }

        when: "trigger.onSessionEnded fires (synchronous gate check + async executor submit)"
        dialecticTrigger.onSessionEnded(SessionEndedEvent.of("agent", sessionKey, "closed"))
        // Allow the striped executor to drain — sleep, do not shut down
        // (the executor bean is shared across spec methods).
        Thread.sleep(500)

        then: "a Tendencies record was written for the user key derived from session key"
        Optional<Tendencies> stored = store.findTendencies("default", TendenciesScope.USER, userKey)
        stored.present
        stored.get().version() == 1L
        stored.get().dialecticPasses() == 1L

        and: "deterministic provider observed brevity + high question rate"
        stored.get().traits()["prefers_brevity"] == "true"
        stored.get().traits()["question_rate"] == "high"
    }

    def "next inbound message gets a <tendencies-context> block spliced in"() {
        given: "a stored Tendencies record"
        Tendencies seeded = Tendencies.forUser("default", userKey,
                "# Profile\nPrefers concise answers.", [prefers_brevity: "true"])
        store.saveTendencies(seeded)

        when:
        MessageReceivedEvent out = userMessageInjector.rewrite(
                MessageReceivedEvent.of("agent", sessionKey, channel, "acct", peer,
                        "What's the deploy status?"))

        then:
        out != null
        out.content().startsWith("<tendencies-context>")
        out.content().contains("Prefers concise")
        out.content().contains("What's the deploy status?")
    }

    def "below-threshold session does NOT trigger a dialectic pass"() {
        given: "only 2 messages — below min-turns=3"
        InMemoryTranscriptSource src = (InMemoryTranscriptSource) transcript
        src.record(sessionKey, "hi")
        src.record(sessionKey, "ok")

        when:
        dialecticTrigger.onSessionEnded(SessionEndedEvent.of("agent", sessionKey, "closed"))
        Thread.sleep(200)

        then: "no Tendencies persisted"
        store.findTendencies("default", TendenciesScope.USER, userKey).empty
    }
}
