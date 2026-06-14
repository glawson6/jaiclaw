package io.jaiclaw.agentmind.tendencies.hook

import com.github.benmanes.caffeine.cache.Caffeine
import io.jaiclaw.core.agent.TendenciesStoreProvider
import io.jaiclaw.core.hook.event.MessageReceivedEvent
import io.jaiclaw.core.model.Tendencies
import io.jaiclaw.core.model.TendenciesScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification

import java.util.Optional

class TendenciesUserMessageInjectorSpec extends Specification {

    TendenciesStoreProvider store = Mock()
    TenantGuard singleTenant = Mock() {
        isMultiTenant() >> false
        requireTenantIfMulti() >> null
    }

    TendenciesUserMessageInjector inj = new TendenciesUserMessageInjector(
            store, singleTenant,
            Caffeine.newBuilder().maximumSize(100).<String, TendenciesUserMessageInjector.CachedBlock>build())

    String userKey(String channel, String peer) {
        TendenciesUserMessageInjector.userKeyFor(channel, peer)
    }

    MessageReceivedEvent eventFor(String channel, String peer, String content) {
        MessageReceivedEvent.of("agent", "agent:" + channel + ":a:" + peer, channel, "a", peer, content)
    }

    // ---------- happy path ----------

    def "rewrite splices <tendencies-context> before the original content"() {
        given:
        String uk = userKey("slack", "U99")
        store.findTendencies("default", TendenciesScope.USER, uk) >>
                Optional.of(Tendencies.forUser("default", uk,
                        "# Profile\nPrefers brevity.", [prefers_brevity: "true"]))

        when:
        MessageReceivedEvent out = inj.rewrite(eventFor("slack", "U99", "What's the status?"))

        then:
        out != null
        out.content().startsWith("<tendencies-context>")
        out.content().contains("Prefers brevity")
        out.content().contains("</tendencies-context>")
        out.content().contains("What's the status?")
        // tendencies block precedes original content
        out.content().indexOf("Prefers brevity") < out.content().indexOf("What's the status?")
    }

    // ---------- no Tendencies, no rewrite ----------

    def "no stored Tendencies → returns null (event unchanged)"() {
        given:
        store.findTendencies(_, _, _) >> Optional.empty()

        when:
        MessageReceivedEvent out = inj.rewrite(eventFor("slack", "U99", "hi"))

        then:
        out == null
    }

    def "stored Tendencies with empty markdown → no rewrite"() {
        given:
        String uk = userKey("slack", "U99")
        store.findTendencies("default", TendenciesScope.USER, uk) >>
                Optional.of(Tendencies.forUser("default", uk, "", [:]))

        when:
        MessageReceivedEvent out = inj.rewrite(eventFor("slack", "U99", "hi"))

        then:
        out == null
    }

    // ---------- missing keys gracefully skip ----------

    def "missing peerId returns null"() {
        when:
        MessageReceivedEvent out = inj.rewrite(eventFor("slack", null, "hi"))

        then:
        out == null
        0 * store.findTendencies(_, _, _)
    }

    def "missing channelId returns null"() {
        when:
        MessageReceivedEvent out = inj.rewrite(eventFor(null, "U99", "hi"))

        then:
        out == null
        0 * store.findTendencies(_, _, _)
    }

    // ---------- store failure is non-fatal ----------

    def "store throwing returns null and does not break message processing"() {
        given:
        store.findTendencies(_, _, _) >> { throw new RuntimeException("disk full") }

        when:
        MessageReceivedEvent out = inj.rewrite(eventFor("slack", "U99", "hi"))

        then:
        out == null
        notThrown(Exception)
    }

    // ---------- caching ----------

    def "second call with the same Tendencies version hits the cache (no second store call)"() {
        given:
        String uk = userKey("slack", "U99")
        store.findTendencies("default", TendenciesScope.USER, uk) >>
                Optional.of(Tendencies.forUser("default", uk, "# x\ny", [:]))

        when: "first call populates cache"
        inj.rewrite(eventFor("slack", "U99", "msg 1"))

        and: "second call with same user — cache hit"
        inj.rewrite(eventFor("slack", "U99", "msg 2"))

        then: "store is consulted both times for findTendencies (caching is on the RENDER step)"
        // Even with caching, we always need to look up the version. The
        // cache saves only the render step. So 2 store hits expected.
        2 * store.findTendencies("default", TendenciesScope.USER, uk) >>
                Optional.of(Tendencies.forUser("default", uk, "# x\ny", [:]))
    }

    def "advance Tendencies version → cache miss → re-render"() {
        given:
        String uk = userKey("slack", "U99")
        Tendencies v0 = Tendencies.forUser("default", uk, "# v0\ny", [:])
        Tendencies v1 = v0.withDialecticResult("# v1\ny", [:])
        // Sequential stubs: first call returns v0, second returns v1.
        store.findTendencies("default", TendenciesScope.USER, uk) >>> [Optional.of(v0), Optional.of(v1)]

        when:
        MessageReceivedEvent firstOut = inj.rewrite(eventFor("slack", "U99", "msg 1"))
        MessageReceivedEvent secondOut = inj.rewrite(eventFor("slack", "U99", "msg 2"))

        then:
        firstOut.content().contains("v0")
        secondOut.content().contains("v1")
        !secondOut.content().contains("v0")
    }

    // ---------- user-key derivation ----------

    def "userKeyFor is deterministic for the same (channel, peer)"() {
        expect:
        TendenciesUserMessageInjector.userKeyFor("slack", "U99") ==
                TendenciesUserMessageInjector.userKeyFor("slack", "U99")
    }

    def "userKeyFor differs across channels for the same peer"() {
        expect:
        TendenciesUserMessageInjector.userKeyFor("slack", "U99") !=
                TendenciesUserMessageInjector.userKeyFor("telegram", "U99")
    }

    def "userKeyFor returns 16-char hex"() {
        expect:
        TendenciesUserMessageInjector.userKeyFor("slack", "U99") ==~ /[0-9a-f]{16}/
    }

    // ---------- render shape ----------

    def "rendered block fences and strips trailing newlines"() {
        given:
        Tendencies t = Tendencies.forUser("default", "u", "# X\nbody\n\n\n", [:])

        when:
        String rendered = TendenciesUserMessageInjector.render(t)

        then:
        rendered.startsWith("<tendencies-context>")
        rendered.endsWith("</tendencies-context>")
        !rendered.contains("\n\n\n")
    }
}
