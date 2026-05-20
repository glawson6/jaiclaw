package io.jaiclaw.rules.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.rules.engine.model.RuleExecutionRequest;
import io.jaiclaw.rules.engine.model.RuleExecutionResponse;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import io.jaiclaw.rules.engine.util.RuleResponseFormatter;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;

/**
 * Tool for executing a named rule set against provided facts.
 */
public class ExecuteRuleTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "ruleName":{"type":"string","description":"Name of the rule set to execute (e.g., 'text-analysis', 'decision', 'validation')"},
            "facts":{"type":"object","description":"Input facts as key-value pairs. Structure depends on the rule: text-analysis expects {text:'...'}, decision expects {parameters:{...}}, validation expects {data:{...}}"},
            "context":{"type":"object","description":"Optional context metadata for rule execution"},
            "enableTrace":{"type":"boolean","description":"Enable detailed execution trace for debugging (default: false)"}
            },"required":["ruleName","facts"]}""";

    private final RuleExecutionService ruleExecutionService;

    public ExecuteRuleTool(RuleExecutionService ruleExecutionService) {
        super(new ToolDefinition("rules_execute",
                "Execute a business rule against provided facts and return results",
                ToolCatalog.SECTION_RULES, INPUT_SCHEMA));
        this.ruleExecutionService = ruleExecutionService;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String ruleName = requireParam(params, "ruleName");
        Map<String, Object> facts = (Map<String, Object>) params.get("facts");
        if (facts == null) {
            throw new IllegalArgumentException("Missing required parameter: facts");
        }

        Map<String, String> ruleContext = null;
        Object contextObj = params.get("context");
        if (contextObj instanceof Map) {
            ruleContext = (Map<String, String>) contextObj;
        }

        Object traceObj = params.get("enableTrace");
        boolean enableTrace = traceObj instanceof Boolean ? (Boolean) traceObj : false;

        RuleExecutionRequest request = RuleExecutionRequest.builder()
                .ruleName(ruleName)
                .facts(facts)
                .context(ruleContext)
                .enableTrace(enableTrace)
                .build();

        RuleExecutionResponse response = ruleExecutionService.executeRule(request);
        return new ToolResult.Success(RuleResponseFormatter.formatAsSimpleString(response));
    }
}
