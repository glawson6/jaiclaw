package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.validation.ValidationReport;
import io.jaiclaw.pipeline.validation.PipelineValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tools that expose the Pipeline Studio authoring plane to agents.
 * Registered under MCP server name {@code pipeline-authoring}; the
 * gateway auto-mounts it at {@code /mcp/pipeline-authoring}.
 *
 * <p>Two tools:
 * <ul>
 *   <li>{@code pipeline_validate} — validates an inline
 *       {@link PipelineDefinition} JSON payload. Always safe: no state
 *       change. Enabled unconditionally.</li>
 *   <li>{@code pipeline_deploy} — deploys a stored draft to the
 *       running Camel context. Gated behind
 *       {@code jaiclaw.pipeline.authoring.mcp.deploy-enabled=true}
 *       (default {@code false}) because deploy from an MCP client
 *       is a privilege escalation surface. Adopters opt in
 *       deliberately.</li>
 * </ul>
 *
 * <p>Both tools honor the {@link TenantContext} passed by the MCP host —
 * the provider sets it on {@link TenantContextHolder} for the duration
 * of the call.
 */
public class PipelineAuthoringMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(PipelineAuthoringMcpToolProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PipelineValidator validator;
    private final PipelineDraftStore draftStore;
    private final PipelineLifecycleManager lifecycle;
    private final boolean deployEnabled;

    public PipelineAuthoringMcpToolProvider(PipelineValidator validator,
                                             PipelineDraftStore draftStore,
                                             PipelineLifecycleManager lifecycle,
                                             boolean deployEnabled) {
        this.validator = validator;
        this.draftStore = draftStore;
        this.lifecycle = lifecycle;
        this.deployEnabled = deployEnabled;
    }

    @Override
    public String getServerName() {
        return "pipeline-authoring";
    }

    @Override
    public String getServerDescription() {
        return "Pipeline Studio authoring tools — validate a definition, and (opt-in) deploy a stored draft.";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        McpToolDefinition validate = new McpToolDefinition(
                "pipeline_validate",
                "Validate an inline PipelineDefinition JSON. Returns a report of every error the framework "
                        + "validator finds. Safe — never mutates registry or draft store.",
                """
                {
                  "type": "object",
                  "properties": {
                    "definition": { "type": "object", "description": "Full PipelineDefinition JSON body" }
                  },
                  "required": ["definition"]
                }""");
        if (!deployEnabled) return List.of(validate);
        McpToolDefinition deploy = new McpToolDefinition(
                "pipeline_deploy",
                "Deploy a stored draft to the live Camel context. Requires "
                        + "jaiclaw.pipeline.authoring.mcp.deploy-enabled=true. Rejects "
                        + "already-deployed ids; use pipeline_undeploy first.",
                """
                {
                  "type": "object",
                  "properties": {
                    "draftId": { "type": "string" }
                  },
                  "required": ["draftId"]
                }""");
        return List.of(validate, deploy);
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (tenant != null) TenantContextHolder.set(tenant);
        try {
            return switch (toolName) {
                case "pipeline_validate" -> handleValidate(args);
                case "pipeline_deploy"   -> handleDeploy(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } finally {
            if (tenant != null) TenantContextHolder.clear();
        }
    }

    // ── handlers ────────────────────────────────────

    private McpToolResult handleValidate(Map<String, Object> args) {
        Object defJson = args == null ? null : args.get("definition");
        if (defJson == null) return McpToolResult.error("Missing required arg: definition");
        try {
            PipelineDefinition def = JSON.convertValue(defJson, PipelineDefinition.class);
            ValidationReport report = validator.validate(def);
            return McpToolResult.success(JSON.writeValueAsString(Map.of(
                    "hasErrors", report.hasErrors(),
                    "totalErrors", report.totalErrors(),
                    "formatted", report.formatted())));
        } catch (Exception e) {
            log.warn("pipeline_validate failed: {}", e.getMessage());
            return McpToolResult.error("Validation failed: " + e.getMessage());
        }
    }

    private McpToolResult handleDeploy(Map<String, Object> args) {
        if (!deployEnabled) {
            return McpToolResult.error("pipeline_deploy is disabled — set "
                    + "jaiclaw.pipeline.authoring.mcp.deploy-enabled=true to enable");
        }
        Object draftIdRaw = args == null ? null : args.get("draftId");
        String draftId = draftIdRaw == null ? null : draftIdRaw.toString();
        if (draftId == null || draftId.isBlank()) {
            return McpToolResult.error("Missing required arg: draftId");
        }
        Optional<PipelineDraft> draft = draftStore.find(draftId);
        if (draft.isEmpty()) return McpToolResult.error("No draft with id: " + draftId);
        try {
            PipelineDefinition deployed = lifecycle.deploy(draft.get().definition(),
                    draft.get().origin() == null ? "STUDIO" : draft.get().origin().name());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pipelineId", deployed.id());
            body.put("status", "DEPLOYED");
            body.put("stageCount", deployed.stages() == null ? 0 : deployed.stages().size());
            return McpToolResult.success(JSON.writeValueAsString(body));
        } catch (PipelineLifecycleManager.AlreadyDeployedException e) {
            return McpToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("pipeline_deploy failed for '{}': {}", draftId, e.getMessage());
            return McpToolResult.error("Deploy failed: " + e.getMessage());
        }
    }
}
