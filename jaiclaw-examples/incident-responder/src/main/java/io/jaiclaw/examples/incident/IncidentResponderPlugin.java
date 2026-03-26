package io.jaiclaw.examples.incident;

import io.jaiclaw.core.agent.ToolCallEvent;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * DevOps incident responder plugin demonstrating explicit tool loop with
 * human-in-the-loop approval and BEFORE/AFTER_TOOL_CALL hooks.
 *
 * <p>The agent triages production incidents by checking service health, querying logs,
 * and proposing remediation actions. Destructive actions (restart, scale) require
 * human approval via the {@link ConsoleApprovalHandler}.
 */
@Component
public class IncidentResponderPlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(IncidentResponderPlugin.class);

    private final List<String> remediationLog = new ArrayList<>();

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "incident-responder-plugin",
                "Incident Responder Plugin",
                "Tools for triaging and remediating production incidents",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new CheckServiceHealthTool());
        api.registerTool(new QueryLogsTool());
        api.registerTool(new RestartServiceTool());
        api.registerTool(new ScaleServiceTool());

        // Log all tool invocations
        api.on(HookName.BEFORE_TOOL_CALL, (event, ctx) -> {
            if (event instanceof ToolCallEvent e) {
                log.info("[HOOK] BEFORE_TOOL_CALL: tool={}, iteration={}, session={}",
                        e.toolName(), e.iterationNumber(), e.sessionKey());
            }
            return null;
        });

        // Track remediation actions
        api.on(HookName.AFTER_TOOL_CALL, (event, ctx) -> {
            if (event instanceof ToolCallEvent e) {
                if ("restart_service".equals(e.toolName()) || "scale_service".equals(e.toolName())) {
                    String entry = "[%s] %s — %s".formatted(Instant.now(), e.toolName(), e.result());
                    remediationLog.add(entry);
                    log.info("[HOOK] Remediation recorded: {}", entry);
                }
            }
            return null;
        });
    }

    // ---- Tools ----

    static class CheckServiceHealthTool implements ToolCallback {

        private final Random random = new Random();

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "check_service_health",
                    "Check the health status and metrics of a production service",
                    "incident-response",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "service": { "type": "string", "description": "Service name (e.g. api-gateway, user-service)" }
                      },
                      "required": ["service"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String service = (String) parameters.get("service");
            String[] statuses = {"healthy", "degraded", "down"};
            String status = statuses[random.nextInt(statuses.length)];
            int latencyMs = status.equals("healthy") ? 45 + random.nextInt(30)
                    : status.equals("degraded") ? 500 + random.nextInt(2000) : -1;
            double errorRate = status.equals("healthy") ? 0.1
                    : status.equals("degraded") ? 12.5 : 95.0;
            int activePods = status.equals("down") ? 0 : 2 + random.nextInt(4);

            return new ToolResult.Success("""
                    Service: %s
                    Status: %s
                    Latency (p99): %s ms
                    Error rate: %.1f%%
                    Active pods: %d
                    Last deploy: 2h ago
                    """.formatted(service, status.toUpperCase(),
                    latencyMs >= 0 ? String.valueOf(latencyMs) : "N/A",
                    errorRate, activePods));
        }
    }

    static class QueryLogsTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "query_logs",
                    "Query recent log entries for a service filtered by severity",
                    "incident-response",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "service": { "type": "string", "description": "Service name" },
                        "severity": { "type": "string", "enum": ["INFO", "WARN", "ERROR"], "description": "Minimum severity" },
                        "minutes": { "type": "integer", "description": "Look-back window in minutes" }
                      },
                      "required": ["service", "severity"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String service = (String) parameters.get("service");
            String severity = (String) parameters.get("severity");
            int minutes = parameters.containsKey("minutes") ? ((Number) parameters.get("minutes")).intValue() : 30;

            return new ToolResult.Success("""
                    Logs for %s (last %d min, severity >= %s):

                    [ERROR] 2 min ago — Connection pool exhausted: max=50, active=50, waiting=23
                    [ERROR] 5 min ago — Timeout calling downstream payment-service (30s exceeded)
                    [WARN]  8 min ago — Circuit breaker OPEN for payment-service (failures=15/20)
                    [WARN]  12 min ago — Memory usage at 87%% of limit (1.74GB / 2GB)
                    [ERROR] 15 min ago — OOMKilled: container restarted by kubelet
                    [INFO]  20 min ago — Health check passed (degraded mode)

                    Total: 6 entries matching criteria
                    """.formatted(service, minutes, severity));
        }
    }

    static class RestartServiceTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "restart_service",
                    "Restart a production service (rolling restart). REQUIRES APPROVAL.",
                    "incident-response",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "service": { "type": "string", "description": "Service name to restart" }
                      },
                      "required": ["service"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String service = (String) parameters.get("service");
            return new ToolResult.Success("""
                    Rolling restart initiated for %s:
                    - Pod %s-0: restarting... ready (12s)
                    - Pod %s-1: restarting... ready (15s)
                    - Pod %s-2: restarting... ready (11s)
                    All pods healthy. Service %s is back to normal.
                    """.formatted(service, service, service, service, service));
        }
    }

    static class ScaleServiceTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "scale_service",
                    "Scale a production service to a specified number of replicas. REQUIRES APPROVAL.",
                    "incident-response",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "service": { "type": "string", "description": "Service name to scale" },
                        "replicas": { "type": "integer", "description": "Target number of replicas" }
                      },
                      "required": ["service", "replicas"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String service = (String) parameters.get("service");
            int replicas = ((Number) parameters.get("replicas")).intValue();
            return new ToolResult.Success("""
                    Scaling %s to %d replicas:
                    - Current: 3 pods
                    - Target: %d pods
                    - New pods provisioned and passing health checks
                    - HPA updated: min=%d, max=%d
                    Scale operation complete.
                    """.formatted(service, replicas, replicas, replicas, replicas * 2));
        }
    }
}
