package io.jaiclaw.rules.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.List;
import java.util.Map;

/**
 * Tool for listing all available rules.
 */
public class ListRulesTool extends AbstractBuiltinTool {

    private final RuleExecutionService ruleExecutionService;

    public ListRulesTool(RuleExecutionService ruleExecutionService) {
        super(new ToolDefinition("rules_list",
                "List all available business rules",
                ToolCatalog.SECTION_RULES));
        this.ruleExecutionService = ruleExecutionService;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        List<String> rules = ruleExecutionService.listAvailableRules();
        StringBuilder sb = new StringBuilder("Available Rules:\n");
        for (String rule : rules) {
            sb.append("  - ").append(rule).append("\n");
        }
        sb.append("Total: ").append(rules.size()).append(" rule(s)");
        return new ToolResult.Success(sb.toString());
    }
}
