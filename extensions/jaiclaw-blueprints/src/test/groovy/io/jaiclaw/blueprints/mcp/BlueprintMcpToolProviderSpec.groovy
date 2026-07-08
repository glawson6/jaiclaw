package io.jaiclaw.blueprints.mcp

import io.jaiclaw.blueprints.BlueprintDefinition
import io.jaiclaw.blueprints.BlueprintRegistry
import io.jaiclaw.blueprints.BlueprintSlot
import spock.lang.Specification

class BlueprintMcpToolProviderSpec extends Specification {

    BlueprintRegistry registry = new BlueprintRegistry()
    BlueprintMcpToolProvider provider = new BlueprintMcpToolProvider(registry)

    def setup() {
        registry.register([
                new BlueprintDefinition("daily-audit", "Daily Audit", "Runs a sweep.",
                        "devops", ["ops"],
                        "0 6 * * *", "Daily at 6 AM",
                        "Run the sweep now.",
                        [BlueprintSlot.text("host_group", "Host group", null,
                                "hosts.yml group")],
                        null),
                new BlueprintDefinition("weekly-review", "Weekly Review", "Reviews PRs.",
                        "github", [],
                        "0 10 * * FRI", "Fridays at 10 AM",
                        "List stale PRs.",
                        [], null)
        ], "test")
    }

    def "exposes the two blueprint tools"() {
        expect:
        provider.tools*.name().toSet() == ["blueprints_list", "blueprints_get"].toSet()
    }

    def "blueprints_list without category returns all"() {
        when:
        def result = provider.execute("blueprints_list", [:], null)

        then:
        !result.isError()
        result.content().contains("daily-audit")
        result.content().contains("weekly-review")
    }

    def "blueprints_list with category filter narrows"() {
        when:
        def result = provider.execute("blueprints_list", [category: "github"], null)

        then:
        !result.isError()
        result.content().contains("weekly-review")
        !result.content().contains("daily-audit")
    }

    def "blueprints_list reports empty result for missing category"() {
        when:
        def result = provider.execute("blueprints_list", [category: "no-such"], null)

        then:
        !result.isError()
        result.content().contains("No blueprints in category")
    }

    def "blueprints_get returns full detail for a real id"() {
        when:
        def result = provider.execute("blueprints_get", [id: "daily-audit"], null)

        then:
        !result.isError()
        def content = result.content()
        content.contains("Daily Audit")
        content.contains("Category: devops")
        content.contains("Slots:")
        content.contains("host_group")
    }

    def "blueprints_get on missing id is an error"() {
        when:
        def result = provider.execute("blueprints_get", [id: "does-not-exist"], null)

        then:
        result.isError()
    }

    def "blueprints_get without id is an error"() {
        when:
        def result = provider.execute("blueprints_get", [:], null)

        then:
        result.isError()
    }

    def "unknown tool is an error"() {
        when:
        def result = provider.execute("blueprints_something", [:], null)

        then:
        result.isError()
    }
}
