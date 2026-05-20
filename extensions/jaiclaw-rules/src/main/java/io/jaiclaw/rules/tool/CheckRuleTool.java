package io.jaiclaw.rules.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;

/**
 * Tool for checking if a specific rule is available.
 */
public class CheckRuleTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "ruleName":{"type":"string","description":"Name of the rule to check availability for"}
            },"required":["ruleName"]}""";

    private final RuleExecutionService ruleExecutionService;

    public CheckRuleTool(RuleExecutionService ruleExecutionService) {
        super(new ToolDefinition("rules_check",
                "Check if a specific business rule is available",
                ToolCatalog.SECTION_RULES, INPUT_SCHEMA));
        this.ruleExecutionService = ruleExecutionService;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String ruleName = requireParam(params, "ruleName");
        boolean available = ruleExecutionService.isRuleAvailable(ruleName);
        String message = available
                ? "Rule '" + ruleName + "' is available and ready for execution."
                : "Rule '" + ruleName + "' is not available. Use rules_list to see available rules.";
        return new ToolResult.Success(message);
    }
}
