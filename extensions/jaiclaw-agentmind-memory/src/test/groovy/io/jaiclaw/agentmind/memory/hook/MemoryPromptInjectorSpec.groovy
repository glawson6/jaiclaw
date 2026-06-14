package io.jaiclaw.agentmind.memory.hook

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties
import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification

import java.util.List
import java.util.Optional

class MemoryPromptInjectorSpec extends Specification {

    AgentMindMemoryProvider provider = Mock()
    TenantGuard singleTenant = Mock() {
        isMultiTenant() >> false
        requireTenantIfMulti() >> null
    }

    AgentMindMemoryProperties propsTenantOn() {
        new AgentMindMemoryProperties(true, null,
                new AgentMindMemoryProperties.Budgets(4096, 2200, 1375),
                new AgentMindMemoryProperties.Rest(false),
                new AgentMindMemoryProperties.Tenant(true, false, List.of("ADMIN", "OPERATOR")))
    }

    AgentMindMemoryProperties propsTenantOff() {
        new AgentMindMemoryProperties(true, null,
                new AgentMindMemoryProperties.Budgets(4096, 2200, 1375),
                new AgentMindMemoryProperties.Rest(false),
                new AgentMindMemoryProperties.Tenant(false, false, List.of("ADMIN", "OPERATOR")))
    }

    String identityHeader() {
        "You are JaiClaw, Personal AI assistant.\n\nRespond directly to the user."
    }

    BeforePromptBuildEvent eventFor(String agentId, String sessionKey, String prompt) {
        BeforePromptBuildEvent.of(agentId, sessionKey, prompt)
    }

    // ---------- composition order ----------

    def "TENANT block renders BEFORE AGENT block BEFORE PEER block"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory("default", MemoryScope.TENANT, null, null) >>
                Optional.of(MemoryDocument.forTenant("default", "# Outages\nSlack #incidents.", 4096))
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("default", "bot", "# Style\nBe brief.", 2200))
        provider.findMemory("default", MemoryScope.PEER, "bot", "U99") >>
                Optional.of(MemoryDocument.forPeer("default", "bot", "U99",
                        "# Preferences\nTechnical depth.", 1375))

        when:
        BeforePromptBuildEvent out = inj.rewrite(
                eventFor("bot", "bot:slack:acct:U99", identityHeader()))

        then:
        out != null
        String p = out.systemPrompt()
        int tenantIdx = p.indexOf("Slack #incidents")
        int agentIdx = p.indexOf("Be brief")
        int peerIdx = p.indexOf("Technical depth")
        tenantIdx > 0
        agentIdx > tenantIdx
        peerIdx > agentIdx
    }

    def "blocks land AFTER the identity line and BEFORE the behaviour preamble"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("default", "bot", "# Notes\nFoo.", 2200))
        provider.findMemory(_, MemoryScope.TENANT, _, _) >> Optional.empty()
        provider.findMemory(_, MemoryScope.PEER, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:a:p", identityHeader()))

        then:
        String p = out.systemPrompt()
        int identityEnd = p.indexOf("\n\n")
        int memoryIdx = p.indexOf("Foo")
        int preamble = p.indexOf("Respond directly to the user")
        identityEnd < memoryIdx
        memoryIdx < preamble
    }

    // ---------- fall-through cases ----------

    def "tenant-only: only the tenant block lands"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory("default", MemoryScope.TENANT, null, null) >>
                Optional.of(MemoryDocument.forTenant("default", "# Org\nUse Slack.", 4096))
        provider.findMemory(_, MemoryScope.AGENT, _, _) >> Optional.empty()
        provider.findMemory(_, MemoryScope.PEER, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:a:p", identityHeader()))

        then:
        out.systemPrompt().contains("Use Slack")
        !out.systemPrompt().contains("Profile")
    }

    def "agent-only: only the agent block lands"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory(_, MemoryScope.TENANT, _, _) >> Optional.empty()
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("default", "bot", "# Notes\nFoo.", 2200))
        provider.findMemory(_, MemoryScope.PEER, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:a:p", identityHeader()))

        then:
        out.systemPrompt().contains("Foo")
        !out.systemPrompt().contains("Use Slack")
    }

    def "peer-only: only the peer block lands"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory(_, MemoryScope.TENANT, _, _) >> Optional.empty()
        provider.findMemory(_, MemoryScope.AGENT, _, _) >> Optional.empty()
        provider.findMemory("default", MemoryScope.PEER, "bot", "U99") >>
                Optional.of(MemoryDocument.forPeer("default", "bot", "U99", "# Bio\nLikes brevity.", 1375))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:acct:U99", identityHeader()))

        then:
        out.systemPrompt().contains("Likes brevity")
    }

    def "all absent: returns null (no prompt change)"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:a:p", identityHeader()))

        then:
        out == null
    }

    // ---------- tenant.enabled gate ----------

    def "TENANT lookup is skipped when jaiclaw.agentmind.memory.tenant.enabled=false"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOff())
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("default", "bot", "# X\ny", 2200))
        provider.findMemory(_, MemoryScope.PEER, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:a:p", identityHeader()))

        then:
        out.systemPrompt().contains("X")
        0 * provider.findMemory(_, MemoryScope.TENANT, _, _)
    }

    // ---------- error tolerance ----------

    def "store throwing on TENANT lookup falls back to AGENT-only injection"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory(_, MemoryScope.TENANT, _, _) >> { throw new RuntimeException("disk full") }
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("default", "bot", "# Notes\nFoo.", 2200))
        provider.findMemory(_, MemoryScope.PEER, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", "bot:slack:a:p", identityHeader()))

        then:
        out.systemPrompt().contains("Foo")
        notThrown(Exception)
    }

    // ---------- session key parsing ----------

    def "peerIdFromSessionKey extracts the last segment"() {
        expect:
        MemoryPromptInjector.peerIdFromSessionKey("bot:slack:acct:U99") == "U99"
    }

    def "peerIdFromSessionKey returns null for malformed keys"() {
        expect:
        MemoryPromptInjector.peerIdFromSessionKey(null) == null
        MemoryPromptInjector.peerIdFromSessionKey("nocolons") == null
        MemoryPromptInjector.peerIdFromSessionKey("bot:slack:acct:") == null
    }

    def "session key without peerId means no PEER lookup"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())
        provider.findMemory(_, MemoryScope.TENANT, _, _) >> Optional.empty()
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("default", "bot", "# X\ny", 2200))

        when:
        inj.rewrite(eventFor("bot", "bot:slack:acct:", identityHeader()))

        then:
        0 * provider.findMemory(_, MemoryScope.PEER, _, _)
    }

    // ---------- null agent context ----------

    def "null agentId returns unchanged"() {
        given:
        MemoryPromptInjector inj = new MemoryPromptInjector(provider, singleTenant, propsTenantOn())

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor(null, "key:k:k:k", identityHeader()))

        then:
        out == null
        0 * provider.findMemory(_, _, _, _)
    }
}
