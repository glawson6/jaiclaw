package io.jaiclaw.pipeline.mcp;

import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.render.PipelineRenderService;
import io.jaiclaw.pipeline.render.RenderProfile;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Hosts four MCP tools that expose JaiClaw pipeline trigger + inspection
 * to external MCP clients (Claude Desktop, other IDE agents). Registered
 * under MCP server name {@code pipeline}; the gateway auto-mounts it at
 * {@code /mcp/pipeline}.
 *
 * <p>The four tools mirror the native agent-tool set in
 * {@code io.jaiclaw.pipeline.tool} — same semantics, MCP wire format.
 *
 * <ul>
 *   <li>{@code pipeline_list} — enumerate registered pipelines.</li>
 *   <li>{@code pipeline_trigger} — fire-and-forget trigger; returns a handle.</li>
 *   <li>{@code pipeline_trigger_sync} — blocking trigger with timeout; returns full result.</li>
 *   <li>{@code pipeline_status} — poll one execution by id.</li>
 * </ul>
 *
 * <p>All four honor the {@link TenantContext} passed by the MCP host — the
 * provider sets it on {@link TenantContextHolder} for the duration of the
 * call so the underlying {@link PipelineGateway} sees the correct tenant.
 */
public class PipelineMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(PipelineMcpToolProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PipelineGateway gateway;
    private final PipelineRegistry registry;
    private final PipelineExecutionTracker tracker;
    private final PipelineRenderService renderService;

    /**
     * Primary constructor. {@code renderService} may be null — the
     * {@code pipeline_render} tool is omitted from {@link #getTools()}
     * in that case.
     */
    public PipelineMcpToolProvider(PipelineGateway gateway,
                                   PipelineRegistry registry,
                                   PipelineExecutionTracker tracker,
                                   PipelineRenderService renderService) {
        this.gateway = gateway;
        this.registry = registry;
        this.tracker = tracker;
        this.renderService = renderService;
    }

    /**
     * Legacy 3-arg constructor kept for source compatibility. Calls the
     * primary constructor with {@code renderService=null}.
     */
    public PipelineMcpToolProvider(PipelineGateway gateway,
                                   PipelineRegistry registry,
                                   PipelineExecutionTracker tracker) {
        this(gateway, registry, tracker, null);
    }

    @Override
    public String getServerName() {
        return "pipeline";
    }

    @Override
    public String getServerDescription() {
        return "Trigger and inspect JaiClaw pipelines";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        List<McpToolDefinition> base = List.of(
                new McpToolDefinition("pipeline_list",
                        "Enumerate every registered JaiClaw pipeline.",
                        SCHEMA_LIST),
                new McpToolDefinition("pipeline_trigger",
                        "Trigger a pipeline fire-and-forget. Returns a handle immediately.",
                        SCHEMA_TRIGGER),
                new McpToolDefinition("pipeline_trigger_sync",
                        "Trigger a pipeline and block up to timeoutSeconds for completion.",
                        SCHEMA_TRIGGER_SYNC),
                new McpToolDefinition("pipeline_status",
                        "Fetch the current status of a pipeline execution by id.",
                        SCHEMA_STATUS)
        );
        if (renderService == null) {
            return base;
        }
        List<McpToolDefinition> combined = new ArrayList<>(base);
        combined.add(new McpToolDefinition("pipeline_render",
                "Render a pipeline as an ASCII visualization. Use view=compact for chat channels, "
                        + "view=table for a rich status board, view=flow for a top-to-bottom stage diagram.",
                SCHEMA_RENDER));
        return combined;
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (tenant != null) {
            TenantContextHolder.set(tenant);
        }
        try {
            return switch (toolName) {
                case "pipeline_list" -> handleList();
                case "pipeline_trigger" -> handleTrigger(args);
                case "pipeline_trigger_sync" -> handleTriggerSync(args);
                case "pipeline_status" -> handleStatus(args);
                case "pipeline_render" -> handleRender(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.warn("MCP tool '{}' failed: {}", toolName, e.toString());
            return McpToolResult.error("Tool '" + toolName + "' failed: " + e.getMessage());
        } finally {
            if (tenant != null) {
                TenantContextHolder.clear();
            }
        }
    }

    // --- tool handlers ---

    private McpToolResult handleList() throws Exception {
        Collection<PipelineDefinition> all = registry.getAll();
        List<Map<String, Object>> rows = new ArrayList<>(all.size());
        for (PipelineDefinition def : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", def.id());
            row.put("name", def.name());
            row.put("description", def.description());
            row.put("enabled", def.enabled());
            row.put("trigger", def.trigger() == null ? "MANUAL" : def.trigger().type().name());
            row.put("stageCount", def.stages() == null ? 0 : def.stages().size());
            if (def.stages() != null) {
                row.put("stageNames", def.stages().stream().map(s -> s.name()).toList());
            }
            rows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", rows.size());
        payload.put("pipelines", rows);
        return McpToolResult.success(JSON.writeValueAsString(payload));
    }

    private McpToolResult handleTrigger(Map<String, Object> args) throws Exception {
        String pipelineId = requireString(args, "pipelineId");
        String input = optionalString(args, "input", "");
        String tenantId = optionalString(args, "tenantId", null);
        String correlationId = optionalString(args, "correlationId", null);

        PipelineExecutionHandle handle;
        if (correlationId != null) {
            handle = gateway.trigger(pipelineId, input, tenantId, correlationId);
        } else if (tenantId != null) {
            handle = gateway.trigger(pipelineId, input, tenantId);
        } else {
            handle = gateway.trigger(pipelineId, input);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("executionId", handle.executionId());
        body.put("pipelineId", handle.pipelineId());
        body.put("submittedAt", handle.submittedAt().toString());
        body.put("status", "SUBMITTED");
        return McpToolResult.success(JSON.writeValueAsString(body));
    }

    private McpToolResult handleTriggerSync(Map<String, Object> args) throws Exception {
        String pipelineId = requireString(args, "pipelineId");
        String input = optionalString(args, "input", "");
        String tenantId = optionalString(args, "tenantId", null);
        String correlationId = optionalString(args, "correlationId", null);
        int timeoutSeconds = optionalInt(args, "timeoutSeconds", 30);
        if (timeoutSeconds <= 0) timeoutSeconds = 30;

        PipelineExecutionResult result = gateway.triggerAndAwait(
                pipelineId, input, tenantId, correlationId,
                Duration.ofSeconds(timeoutSeconds));
        return McpToolResult.success(JSON.writeValueAsString(result));
    }

    private McpToolResult handleStatus(Map<String, Object> args) throws Exception {
        String executionId = requireString(args, "executionId");
        Optional<PipelineExecutionSummary> found = tracker.byId(executionId);
        if (found.isEmpty()) {
            return McpToolResult.error("Execution not found: " + executionId);
        }
        return McpToolResult.success(JSON.writeValueAsString(found.get()));
    }

    private McpToolResult handleRender(Map<String, Object> args) {
        if (renderService == null) {
            return McpToolResult.error("Render service not available");
        }
        try {
            String pipelineId = requireString(args, "pipelineId");
            String executionId = optionalString(args, "executionId", null);
            PipelineRenderService.View view = PipelineRenderService.View.fromString(
                    optionalString(args, "view", "compact"));
            RenderProfile profile = RenderProfile.fromString(
                    optionalString(args, "profile", "shell_80"));
            String rendered = renderService.renderAscii(pipelineId, executionId, view, profile);
            return McpToolResult.success(rendered);
        } catch (IllegalArgumentException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    // --- helpers ---

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return v.toString();
    }

    private static String optionalString(Map<String, Object> args, String key, String defaultValue) {
        Object v = args == null ? null : args.get(key);
        return (v == null || v.toString().isBlank()) ? defaultValue : v.toString();
    }

    private static int optionalInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args == null ? null : args.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // --- JSON schemas ---

    private static final String SCHEMA_LIST = """
            {
              "type": "object",
              "properties": {}
            }""";

    private static final String SCHEMA_TRIGGER = """
            {
              "type": "object",
              "properties": {
                "pipelineId":    {"type": "string", "description": "Pipeline id to trigger."},
                "input":         {"type": "string", "description": "Payload for the first stage; empty when omitted."},
                "tenantId":      {"type": "string", "description": "Tenant to execute under (multi-tenant deployments)."},
                "correlationId": {"type": "string", "description": "Optional distributed-tracing correlation id."}
              },
              "required": ["pipelineId"]
            }""";

    private static final String SCHEMA_TRIGGER_SYNC = """
            {
              "type": "object",
              "properties": {
                "pipelineId":     {"type": "string", "description": "Pipeline id to trigger."},
                "input":          {"type": "string", "description": "Payload for the first stage; empty when omitted."},
                "tenantId":       {"type": "string", "description": "Tenant to execute under (multi-tenant deployments)."},
                "correlationId":  {"type": "string", "description": "Optional distributed-tracing correlation id."},
                "timeoutSeconds": {"type": "integer", "description": "Wait up to this many seconds (default 30).", "minimum": 1}
              },
              "required": ["pipelineId"]
            }""";

    private static final String SCHEMA_STATUS = """
            {
              "type": "object",
              "properties": {
                "executionId": {"type": "string", "description": "The executionId returned from pipeline_trigger."}
              },
              "required": ["executionId"]
            }""";

    private static final String SCHEMA_RENDER = """
            {
              "type": "object",
              "properties": {
                "pipelineId":   {"type": "string", "description": "Pipeline id to render (see pipeline_list)."},
                "executionId":  {"type": "string", "description": "Optional — specific execution. Defaults to the most recent."},
                "view":         {"type": "string", "enum": ["compact", "table", "flow"], "description": "compact (default) = one-liner-per-stage. table = multi-column status board. flow = top-to-bottom diagram."},
                "profile":      {"type": "string", "enum": ["shell_80", "shell_120", "slack_desktop", "slack_mobile", "telegram_desktop", "telegram_mobile", "discord_desktop", "discord_mobile", "email"], "description": "Render-width profile. Defaults to shell_80."}
              },
              "required": ["pipelineId"]
            }""";
}
