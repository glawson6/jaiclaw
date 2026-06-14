package io.jaiclaw.agentmind.tendencies.honcho

import io.jaiclaw.core.model.Tendencies
import spock.lang.Specification

import java.util.List
import java.util.Optional

class HonchoRemoteTendenciesProviderSpec extends Specification {

    HonchoClient client = Mock()
    HonchoRemoteTendenciesProvider provider = new HonchoRemoteTendenciesProvider(client)

    Tendencies blank(String tid, String uid) {
        Tendencies.forUser(tid, uid, "", [:])
    }

    def "type identifier is 'honcho'"() {
        expect:
        provider.type() == "honcho"
    }

    def "empty transcript returns empty (no client call)"() {
        when:
        Optional<Tendencies> out = provider.learn(blank("acme", "u-1"), [])

        then:
        out.empty
        0 * client.dialectic(_, _, _)
    }

    def "null transcript returns empty"() {
        when:
        Optional<Tendencies> out = provider.learn(blank("acme", "u-1"), null)

        then:
        out.empty
    }

    def "delegates to client with workspace=tenant + peerName=userId"() {
        given:
        Tendencies current = blank("acme", "u-1")
        client.dialectic("acme", "u-1", ["msg1", "msg2"]) >>
                Optional.of(new HonchoClient.HonchoDialecticResult("# Profile\nFoo.", [a: "1"]))

        when:
        Optional<Tendencies> out = provider.learn(current, ["msg1", "msg2"])

        then:
        out.present
        out.get().peerCardMarkdown().contains("Foo")
        out.get().traits() == [a: "1"]
        out.get().version() == 1L
        out.get().dialecticPasses() == 1L
    }

    def "client returning empty → provider returns empty"() {
        given:
        client.dialectic(_, _, _) >> Optional.empty()

        when:
        Optional<Tendencies> out = provider.learn(blank("acme", "u-1"), ["msg"])

        then:
        out.empty
    }

    def "client throwing is caught and logged; provider returns empty"() {
        given:
        client.dialectic(_, _, _) >> { throw new RuntimeException("network down") }

        when:
        Optional<Tendencies> out = provider.learn(blank("acme", "u-1"), ["msg"])

        then:
        out.empty
        notThrown(Exception)
    }

    def "NoOpHonchoClient always returns empty (suitable for demo)"() {
        when:
        Optional<HonchoClient.HonchoDialecticResult> r =
                new NoOpHonchoClient().dialectic("acme", "u-1", ["msg"])

        then:
        r.empty
    }
}
