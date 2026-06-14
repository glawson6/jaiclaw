package io.jaiclaw.hermes.soul.tool

import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

import java.util.Optional

class SoulAgentToolSpec extends Specification {

    SoulProvider provider = Mock()
    TenantGuard singleTenant = Mock() {
        isMultiTenant() >> false
    }

    SoulAgentTool tool = new SoulAgentTool(provider, singleTenant)
    ToolContext ctx = new ToolContext("bot", "default:slack:acct:user", "sid", ".")

    def "definition exposes the soul tool name and section"() {
        expect:
        tool.definition().name() == "soul"
        tool.definition().section() == "Soul"
        tool.definition().inputSchema().contains("\"action\"")
    }

    // ---------- add ----------

    def "add creates a new Soul when none exists"() {
        given:
        provider.findSoul("default", SoulScope.AGENT, "bot") >> Optional.empty()

        when:
        ToolResult result = tool.execute(
                [action: "add", heading: "Identity", body: "I am helpful."], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveSoul({ Soul s ->
            s.scope() == SoulScope.AGENT &&
                s.agentId() == "bot" &&
                s.markdown().contains("# Identity") &&
                s.markdown().contains("I am helpful.") &&
                s.version() == 0L
        })
    }

    def "add updates an existing Soul and bumps the version"() {
        given:
        Soul existing = new Soul(SoulScope.AGENT, "default", "bot",
                "# Style\nold\n", java.time.Instant.now(), 3L)
        provider.findSoul("default", SoulScope.AGENT, "bot") >> Optional.of(existing)

        when:
        ToolResult result = tool.execute(
                [action: "add", heading: "Style", body: "new"], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveSoul({ Soul s ->
            s.markdown().contains("new") &&
                !s.markdown().contains("old") &&
                s.version() == 4L
        })
    }

    // ---------- replace ----------

    def "replace returns Error when the section does not exist"() {
        given:
        provider.findSoul(_, _, _) >> Optional.empty()

        when:
        ToolResult result = tool.execute(
                [action: "replace", heading: "Style", body: "new"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Style")
        0 * provider.saveSoul(_)
    }

    // ---------- remove ----------

    def "remove succeeds idempotently when the section is missing"() {
        given:
        provider.findSoul("default", SoulScope.AGENT, "bot") >> Optional.empty()

        when:
        ToolResult result = tool.execute(
                [action: "remove", heading: "Style"], ctx)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("No change")
        0 * provider.saveSoul(_)
    }

    def "remove drops a section and writes a bumped version"() {
        given:
        Soul existing = new Soul(SoulScope.AGENT, "default", "bot",
                "# Identity\nhelpful\n\n# Style\nconcise\n",
                java.time.Instant.now(), 5L)
        provider.findSoul("default", SoulScope.AGENT, "bot") >> Optional.of(existing)

        when:
        ToolResult result = tool.execute(
                [action: "remove", heading: "Style"], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveSoul({ Soul s ->
            !s.markdown().contains("# Style") &&
                s.markdown().contains("# Identity") &&
                s.version() == 6L
        })
    }

    // ---------- scope=TENANT rejection ----------

    def "scope=TENANT is rejected as operator-only"() {
        when:
        ToolResult result = tool.execute(
                [action: "add", heading: "Identity", body: "x", scope: "TENANT"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("operator-only")
        0 * provider.findSoul(_, _, _)
        0 * provider.saveSoul(_)
    }

    def "scope=tenant (lowercase) is also rejected — case-insensitive"() {
        when:
        ToolResult result = tool.execute(
                [action: "add", heading: "Identity", body: "x", scope: "tenant"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("operator-only")
    }

    // ---------- input validation ----------

    def "missing action is rejected"() {
        when:
        ToolResult result = tool.execute([heading: "Identity", body: "x"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("action")
    }

    def "unknown action is rejected"() {
        when:
        ToolResult result = tool.execute(
                [action: "delete", heading: "Identity"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unknown action")
    }

    def "blank heading is rejected"() {
        when:
        ToolResult result = tool.execute(
                [action: "add", heading: "   ", body: "x"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("heading")
    }
}
