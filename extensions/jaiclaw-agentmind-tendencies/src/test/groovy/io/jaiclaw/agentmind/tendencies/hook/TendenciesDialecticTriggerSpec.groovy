package io.jaiclaw.agentmind.tendencies.hook

import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor
import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider
import io.jaiclaw.agentmind.tendencies.transcript.TranscriptSource
import io.jaiclaw.core.agent.TendenciesStoreProvider
import io.jaiclaw.core.hook.event.SessionEndedEvent
import io.jaiclaw.core.model.Tendencies
import io.jaiclaw.core.model.TendenciesScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification

import java.util.List
import java.util.Optional

class TendenciesDialecticTriggerSpec extends Specification {

    TranscriptSource transcript = Mock()
    TendenciesCadenceGate gate = Mock()
    StripedDialecticExecutor executor = new StripedDialecticExecutor(10)
    TendenciesStoreProvider store = Mock()
    TendenciesLearningProvider learning = Mock()
    TenantGuard singleTenant = Mock() {
        isMultiTenant() >> false
        requireTenantIfMulti() >> null
    }

    TendenciesDialecticTrigger trigger = new TendenciesDialecticTrigger(
            transcript, gate, executor, store, learning, singleTenant)

    SessionEndedEvent eventFor(String channel, String peer) {
        SessionEndedEvent.of("agent", "agent:" + channel + ":a:" + peer, "closed")
    }

    void awaitExecutor() {
        // The trigger submits work to the executor; let it drain.
        Thread.sleep(150)
    }

    def cleanup() {
        executor.shutdownAndAwait(500)
    }

    // ---------- gate-blocked cases ----------

    def "session below minTurns is skipped (cadence gate miss)"() {
        given:
        SessionEndedEvent event = eventFor("slack", "U99")
        transcript.recentMessages(event.sessionKey()) >> List.of("ok", "thanks")
        gate.shouldRun("default", _, 2) >> false

        when:
        trigger.onSessionEnded(event)
        awaitExecutor()

        then:
        0 * store.findTendencies(_, _, _)
        0 * learning.learn(_, _)
    }

    def "gate allows but transcript is empty → learning produces nothing → no save"() {
        given:
        SessionEndedEvent event = eventFor("slack", "U99")
        transcript.recentMessages(event.sessionKey()) >> List.of("a", "b", "c", "d", "e")
        gate.shouldRun(_, _, 5) >> true
        String uk = TendenciesUserMessageInjector.userKeyFor("slack", "U99")
        store.findTendencies("default", TendenciesScope.USER, uk) >> Optional.empty()
        learning.learn(_, _) >> Optional.empty()

        when:
        trigger.onSessionEnded(event)
        awaitExecutor()

        then:
        0 * store.saveTendencies(_)
        0 * gate.recordRun(_, _)
    }

    // ---------- happy path ----------

    def "gate allows + learning returns updated Tendencies → save + recordRun"() {
        given:
        SessionEndedEvent event = eventFor("slack", "U99")
        String uk = TendenciesUserMessageInjector.userKeyFor("slack", "U99")
        Tendencies current = Tendencies.forUser("default", uk, "", [:])
        Tendencies updated = current.withDialecticResult("# X\ny", [a: "1"])
        transcript.recentMessages(event.sessionKey()) >> List.of("a", "b", "c", "d", "e")
        gate.shouldRun(_, _, 5) >> true
        store.findTendencies("default", TendenciesScope.USER, uk) >> Optional.of(current)
        learning.learn(current, _) >> Optional.of(updated)

        when:
        trigger.onSessionEnded(event)
        awaitExecutor()

        then:
        1 * store.saveTendencies({ Tendencies t -> t.peerCardMarkdown() == "# X\ny" })
        1 * gate.recordRun("default", uk)
    }

    def "uses an empty fresh Tendencies when none stored yet"() {
        given:
        SessionEndedEvent event = eventFor("slack", "U99")
        transcript.recentMessages(event.sessionKey()) >> List.of("a", "b", "c", "d", "e")
        gate.shouldRun(_, _, _) >> true
        store.findTendencies(_, _, _) >> Optional.empty()

        when:
        trigger.onSessionEnded(event)
        awaitExecutor()

        then:
        1 * learning.learn({ Tendencies t ->
            t.version() == 0L && t.peerCardMarkdown() == ""
        }, _) >> Optional.empty()
    }

    // ---------- error tolerance ----------

    def "learning provider throwing does NOT propagate (stripe stays clean)"() {
        given:
        SessionEndedEvent event = eventFor("slack", "U99")
        transcript.recentMessages(event.sessionKey()) >> List.of("a", "b", "c", "d", "e")
        gate.shouldRun(_, _, _) >> true
        store.findTendencies(_, _, _) >> Optional.empty()
        learning.learn(_, _) >> { throw new RuntimeException("boom") }

        when:
        trigger.onSessionEnded(event)
        awaitExecutor()

        then:
        0 * store.saveTendencies(_)
        0 * gate.recordRun(_, _)
        notThrown(Exception)
    }

    // ---------- session key parsing ----------

    def "userKeyFromSessionKey returns null for malformed keys"() {
        expect:
        TendenciesDialecticTrigger.userKeyFromSessionKey(input) == null

        where:
        input << [null, "nocolons", "a:b:c"]
    }

    def "userKeyFromSessionKey matches the injector's key derivation"() {
        expect:
        TendenciesDialecticTrigger.userKeyFromSessionKey("agent:slack:acct:U99") ==
                TendenciesUserMessageInjector.userKeyFor("slack", "U99")
    }
}
