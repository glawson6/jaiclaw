package io.jaiclaw.rules.mcp;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.rules.engine.model.RuleExecutionRequest;
import io.jaiclaw.rules.engine.model.RuleExecutionResponse;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import io.jaiclaw.rules.engine.util.RuleResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * MCP tool provider exposing rules engine tools.
 * Server name: {@code rules}, with 3 tools for rule execution, listing, and checking.
 */
public class RulesMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(RulesMcpToolProvider.class);
    private static final String SERVER_NAME = "rules";
    private static final String SERVER_DESCRIPTION = "Business rules engine — execute, list, and check availability of Drools-based business rules";

    private final RuleExecutionService ruleExecutionService;
    private final ObjectMapper objectMapper;

    public RulesMcpToolProvider(RuleExecutionService ruleExecutionService, ObjectMapper objectMapper) {
        this.ruleExecutionService = ruleExecutionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("execute_rule", "Execute a business rule against provided facts", EXECUTE_SCHEMA),
                new McpToolDefinition("list_rules", "List all available business rules", LIST_SCHEMA),
                new McpToolDefinition("check_rule", "Check if a specific business rule is available", CHECK_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "execute_rule" -> handleExecuteRule(args);
                case "list_rules" -> handleListRules();
                case "check_rule" -> handleCheckRule(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private McpToolResult handleExecuteRule(Map<String, Object> args) throws JacksonException {
        String ruleName = requireString(args, "ruleName");
        Map<String, Object> facts = (Map<String, Object>) args.get("facts");
        if (facts == null) {
            return McpToolResult.error("Missing required parameter: facts");
        }

        Map<String, String> context = null;
        Object contextObj = args.get("context");
        if (contextObj instanceof Map) {
            context = (Map<String, String>) contextObj;
        }

        Object traceObj = args.get("enableTrace");
        boolean enableTrace = traceObj instanceof Boolean ? (Boolean) traceObj : false;

        RuleExecutionRequest request = RuleExecutionRequest.builder()
                .ruleName(ruleName)
                .facts(facts)
                .context(context)
                .enableTrace(enableTrace)
                .build();

        RuleExecutionResponse response = ruleExecutionService.executeRule(request);
        return McpToolResult.success(RuleResponseFormatter.formatAsSimpleString(response));
    }

    private McpToolResult handleListRules() throws JacksonException {
        List<String> rules = ruleExecutionService.listAvailableRules();
        return McpToolResult.success(objectMapper.writeValueAsString(
                Map.of("rules", rules, "count", rules.size())));
    }

    private McpToolResult handleCheckRule(Map<String, Object> args) throws JacksonException {
        String ruleName = requireString(args, "ruleName");
        boolean available = ruleExecutionService.isRuleAvailable(ruleName);
        return McpToolResult.success(objectMapper.writeValueAsString(
                Map.of("ruleName", ruleName, "available", available)));
    }

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return value.toString();
    }

    private static final String EXECUTE_SCHEMA = """
            {"type":"object","properties":{"ruleName":{"type":"string","description":"Name of the rule set to execute"},\
            "facts":{"type":"object","description":"Input facts as key-value pairs"},\
            "context":{"type":"object","description":"Optional context metadata"},\
            "enableTrace":{"type":"boolean","description":"Enable detailed execution trace"}},"required":["ruleName","facts"]}""";

    private static final String LIST_SCHEMA = """
            {"type":"object","properties":{}}""";

    private static final String CHECK_SCHEMA = """
            {"type":"object","properties":{"ruleName":{"type":"string","description":"Name of the rule to check"}},"required":["ruleName"]}""";
}
