package io.jaiclaw.rules.tool

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.rules.engine.service.RuleExecutionService
import spock.lang.Specification
import spock.lang.Subject

class ListRulesToolSpec extends Specification {

    RuleExecutionService ruleExecutionService = Mock()

    @Subject
    ListRulesTool tool = new ListRulesTool(ruleExecutionService)

    def ctx = new ToolContext(null, null, null, null)

    def "should have correct tool definition"() {
        expect:
        tool.definition().name() == "rules_list"
        tool.definition().section() == "Rules"
    }

    def "should list available rules"() {
        given:
        ruleExecutionService.listAvailableRules() >> ["text-analysis", "decision", "validation"]

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success
        def content = ((ToolResult.Success) result).content()
        content.contains("text-analysis")
        content.contains("decision")
        content.contains("validation")
        content.contains("Total: 3 rule(s)")
    }

    def "should handle empty rule list"() {
        given:
        ruleExecutionService.listAvailableRules() >> []

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success
        def content = ((ToolResult.Success) result).content()
        content.contains("Total: 0 rule(s)")
    }
}
