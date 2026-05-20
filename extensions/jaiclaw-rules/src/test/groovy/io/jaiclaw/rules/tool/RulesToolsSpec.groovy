package io.jaiclaw.rules.tool

import io.jaiclaw.rules.engine.service.RuleExecutionService
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class RulesToolsSpec extends Specification {

    RuleExecutionService ruleExecutionService = Mock()

    def "should create all three tools"() {
        when:
        def tools = RulesTools.all(ruleExecutionService)

        then:
        tools.size() == 3
        tools.find { it.definition().name() == "rules_execute" } != null
        tools.find { it.definition().name() == "rules_list" } != null
        tools.find { it.definition().name() == "rules_check" } != null
    }

    def "should register all tools with registry"() {
        given:
        def registry = Mock(ToolRegistry)

        when:
        RulesTools.registerAll(registry, ruleExecutionService)

        then:
        1 * registry.registerAll({ it.size() == 3 })
    }

    def "all tools should be in Rules section"() {
        when:
        def tools = RulesTools.all(ruleExecutionService)

        then:
        tools.every { it.definition().section() == "Rules" }
    }
}
