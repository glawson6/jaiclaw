package io.jaiclaw.rules.tool

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.rules.engine.model.RuleExecutionRequest
import io.jaiclaw.rules.engine.model.RuleExecutionResponse
import io.jaiclaw.rules.engine.service.RuleExecutionService
import spock.lang.Specification
import spock.lang.Subject

class ExecuteRuleToolSpec extends Specification {

    RuleExecutionService ruleExecutionService = Mock()

    @Subject
    ExecuteRuleTool tool = new ExecuteRuleTool(ruleExecutionService)

    def "should have correct tool definition"() {
        expect:
        tool.definition().name() == "rules_execute"
        tool.definition().section() == "Rules"
        tool.definition().inputSchema().contains("ruleName")
    }

    def ctx = new ToolContext(null, null, null, null)

    def "should execute rule successfully"() {
        given:
        def params = [
            ruleName: "text-analysis",
            facts: [text: "This is great news"]
        ]
        def response = RuleExecutionResponse.builder()
            .success(true)
            .ruleName("text-analysis")
            .results([sentiment: "positive"])
            .messages(["Rule 'text-analysis' executed successfully"])
            .build()

        when:
        def result = tool.execute(params, ctx)

        then:
        1 * ruleExecutionService.executeRule(_ as RuleExecutionRequest) >> response
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("SUCCESS")
        ((ToolResult.Success) result).content().contains("text-analysis")
    }

    def "should return error when ruleName is missing"() {
        given:
        def params = [facts: [text: "hello"]]

        when:
        def result = tool.execute(params, ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Missing required parameter: ruleName")
    }

    def "should return error when facts is missing"() {
        given:
        def params = [ruleName: "text-analysis"]

        when:
        def result = tool.execute(params, ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Missing required parameter: facts")
    }

    def "should handle rule execution failure"() {
        given:
        def params = [
            ruleName: "unknown-rule",
            facts: [text: "test"]
        ]
        def response = RuleExecutionResponse.builder()
            .success(false)
            .ruleName("unknown-rule")
            .error("Unknown rule: unknown-rule")
            .build()

        when:
        def result = tool.execute(params, ctx)

        then:
        1 * ruleExecutionService.executeRule(_ as RuleExecutionRequest) >> response
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("FAILED")
    }

    def "should pass enableTrace flag"() {
        given:
        def params = [
            ruleName: "text-analysis",
            facts: [text: "test"],
            enableTrace: true
        ]
        def response = RuleExecutionResponse.builder()
            .success(true)
            .ruleName("text-analysis")
            .results([:])
            .messages(["Rule executed"])
            .trace(["Fact type: TextAnalysisFact"])
            .build()

        when:
        tool.execute(params, ctx)

        then:
        1 * ruleExecutionService.executeRule({ RuleExecutionRequest req ->
            req.enableTrace == true
        }) >> response
    }
}
