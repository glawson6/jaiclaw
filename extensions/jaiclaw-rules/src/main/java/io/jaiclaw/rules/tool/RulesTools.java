package io.jaiclaw.rules.tool;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all rules tools.
 */
public final class RulesTools {

    private RulesTools() {}

    public static List<ToolCallback> all(RuleExecutionService ruleExecutionService) {
        return List.of(
                new ExecuteRuleTool(ruleExecutionService),
                new ListRulesTool(ruleExecutionService),
                new CheckRuleTool(ruleExecutionService)
        );
    }

    public static void registerAll(ToolRegistry registry, RuleExecutionService ruleExecutionService) {
        registry.registerAll(all(ruleExecutionService));
    }
}
