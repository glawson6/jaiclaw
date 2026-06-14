package io.jaiclaw.agentmind.soul.hook

import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.agentmind.soul.AgentMindSoulProperties
import spock.lang.Specification

import java.util.List

/**
 * Plan §5 tasks 1.6 + 1.17 — verifies the injector produces the right
 * composition order (TENANT then AGENT), omits empty scopes, splices after
 * the identity line, and tolerates missing tenant context.
 */
class SoulPromptInjectorSpec extends Specification {

    SoulProvider soulProvider = Mock()
    TenantGuard singleTenant = Mock() {
        isMultiTenant() >> false
        requireTenantIfMulti() >> null
    }

    AgentMindSoulProperties propsWithTenant() {
        new AgentMindSoulProperties(true, null, new AgentMindSoulProperties.Rest(false),
                new AgentMindSoulProperties.Tenant(true, List.of("ADMIN", "OPERATOR")))
    }

    AgentMindSoulProperties propsTenantDisabled() {
        new AgentMindSoulProperties(true, null, new AgentMindSoulProperties.Rest(false),
                new AgentMindSoulProperties.Tenant(false, List.of("ADMIN", "OPERATOR")))
    }

    String identityHeader() {
        "You are JaiClaw, Personal AI assistant.\n\nRespond directly to the user."
    }

    BeforePromptBuildEvent eventFor(String agentId, String prompt) {
        BeforePromptBuildEvent.of(agentId, "key", prompt)
    }

    // ---------- composition order ----------

    def "tenant Soul renders BEFORE agent Soul"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul("default", SoulScope.TENANT, null) >>
                Optional.of(Soul.forTenant("default", "# Style\nBullet points."))
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("default", "bot", "# Style\nTechnical tone."))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out != null
        int tenantIdx = out.systemPrompt().indexOf("Bullet points")
        int agentIdx = out.systemPrompt().indexOf("Technical tone")
        tenantIdx > 0
        agentIdx > tenantIdx
    }

    def "both blocks are spliced AFTER the identity line"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul("default", SoulScope.TENANT, null) >>
                Optional.of(Soul.forTenant("default", "# Identity\nOrg voice."))
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("default", "bot", "# Identity\nAgent voice."))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out.systemPrompt().startsWith("You are JaiClaw, Personal AI assistant.\n\n")
        out.systemPrompt().indexOf("Org voice") > "You are JaiClaw, Personal AI assistant.\n\n".length() - 1
        out.systemPrompt().indexOf("Respond directly to the user") > out.systemPrompt().indexOf("Agent voice")
    }

    // ---------- fall-through cases ----------

    def "tenant-only: only the tenant block lands, no agent block"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul("default", SoulScope.TENANT, null) >>
                Optional.of(Soul.forTenant("default", "# Style\nBullet points."))
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out.systemPrompt().contains("Bullet points")
        !out.systemPrompt().contains("Technical")
    }

    def "agent-only: only the agent block lands, no tenant block"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul("default", SoulScope.TENANT, null) >> Optional.empty()
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("default", "bot", "# Style\nTechnical."))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out.systemPrompt().contains("Technical")
        !out.systemPrompt().contains("Bullet")
    }

    def "both absent: returns null (unchanged signal) — prefix cache stays warm"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul(_, _, _) >> Optional.empty()

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out == null
    }

    // ---------- empty-section omission ----------

    def "empty markdown is treated as absent — no placeholder header"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul("default", SoulScope.TENANT, null) >>
                Optional.of(Soul.forTenant("default", ""))
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("default", "bot", "# Identity\nAgent voice."))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out.systemPrompt().contains("Agent voice")
        out.systemPrompt().indexOf("\n\n\n") == -1 // no double-blank from empty tenant block
    }

    // ---------- tenant.enabled gate ----------

    def "tenant scope skipped when jaiclaw.agentmind.soul.tenant.enabled=false"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsTenantDisabled())
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("default", "bot", "# Identity\nAgent."))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out.systemPrompt().contains("Agent")
        // tenant lookup must NOT happen
        0 * soulProvider.findSoul("default", SoulScope.TENANT, _)
    }

    // ---------- error tolerance ----------

    def "store throwing on TENANT lookup falls back to AGENT-only injection"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())
        soulProvider.findSoul("default", SoulScope.TENANT, null) >> { throw new RuntimeException("disk full") }
        soulProvider.findSoul("default", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("default", "bot", "# Identity\nAgent."))

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor("bot", identityHeader()))

        then:
        out.systemPrompt().contains("Agent")
        notThrown(Exception)
    }

    def "null agentId is rejected gracefully — returns unchanged"() {
        given:
        SoulPromptInjector inj = new SoulPromptInjector(soulProvider, singleTenant, propsWithTenant())

        when:
        BeforePromptBuildEvent out = inj.rewrite(eventFor(null, identityHeader()))

        then:
        out == null
        0 * soulProvider.findSoul(_, _, _)
    }
}
