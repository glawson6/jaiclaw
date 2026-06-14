package io.jaiclaw.agentmind.tendencies.mcp

import io.jaiclaw.core.agent.TendenciesStoreProvider
import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.core.model.Tendencies
import io.jaiclaw.core.model.TendenciesScope
import io.jaiclaw.core.tenant.TenantContext
import spock.lang.Specification

import java.util.Optional

class TendenciesMcpToolProviderSpec extends Specification {

    TendenciesStoreProvider store = Mock()
    TendenciesMcpToolProvider provider = new TendenciesMcpToolProvider(store)

    TenantContext tenant(String tid) {
        TenantContext c = Mock()
        c.getTenantId() >> tid
        return c
    }

    def "server name and tool catalog"() {
        expect:
        provider.serverName == "agentmind-tendencies"
        provider.tools.collect { it.name() } as Set ==
                ["tendencies_observe", "tendencies_query"] as Set
    }

    // ---------- tendencies_observe ----------

    def "observe returns version + traits + markdown when present"() {
        given:
        Tendencies t = Tendencies.forUser("acme", "u-1", "# X\nbody", [a: "1", b: "2"])
                .withDialecticResult("# X\nbody", [a: "1", b: "2"])
        store.findTendencies("acme", TendenciesScope.USER, "u-1") >> Optional.of(t)

        when:
        McpToolResult r = provider.execute("tendencies_observe", [userKey: "u-1"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("version:")
        r.content().contains("dialecticPasses:")
        r.content().contains("traits:")
        r.content().contains("body")
    }

    def "observe with missing userKey returns error"() {
        when:
        McpToolResult r = provider.execute("tendencies_observe", [:], tenant("acme"))

        then:
        r.isError()
        r.content().contains("userKey")
    }

    def "observe with no stored Tendencies returns error"() {
        given:
        store.findTendencies(_, _, _) >> Optional.empty()

        when:
        McpToolResult r = provider.execute("tendencies_observe",
                [userKey: "ghost"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("No Tendencies found")
    }

    // ---------- tendencies_query ----------

    def "query returns trait value when present"() {
        given:
        Tendencies t = Tendencies.forUser("acme", "u-1", "x", [prefers_brevity: "true"])
        store.findTendencies("acme", TendenciesScope.USER, "u-1") >> Optional.of(t)

        when:
        McpToolResult r = provider.execute("tendencies_query",
                [userKey: "u-1", traitKey: "prefers_brevity"], tenant("acme"))

        then:
        !r.isError()
        r.content() == "true"
    }

    def "query returns 'absent' when the trait is not present"() {
        given:
        Tendencies t = Tendencies.forUser("acme", "u-1", "x", [other: "value"])
        store.findTendencies(_, _, _) >> Optional.of(t)

        when:
        McpToolResult r = provider.execute("tendencies_query",
                [userKey: "u-1", traitKey: "prefers_brevity"], tenant("acme"))

        then:
        !r.isError()
        r.content() == "absent"
    }

    def "query returns 'absent' when there is no Tendencies record at all"() {
        given:
        store.findTendencies(_, _, _) >> Optional.empty()

        when:
        McpToolResult r = provider.execute("tendencies_query",
                [userKey: "ghost", traitKey: "x"], tenant("acme"))

        then:
        !r.isError()
        r.content() == "absent"
    }

    def "query with missing parameters returns error"() {
        when:
        McpToolResult r = provider.execute("tendencies_query",
                [userKey: "u-1"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("traitKey")
    }

    def "unknown tool name returns error"() {
        when:
        McpToolResult r = provider.execute("tendencies_write", [:], tenant("acme"))

        then:
        r.isError()
        r.content().contains("Unknown tool")
    }
}
