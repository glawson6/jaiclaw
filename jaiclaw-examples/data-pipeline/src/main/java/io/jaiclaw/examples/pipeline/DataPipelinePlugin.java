package io.jaiclaw.examples.pipeline;

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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data pipeline orchestrator plugin demonstrating explicit tool loop with
 * audit trail hooks and human approval for destructive operations.
 *
 * <p>The agent manages ETL pipelines: validates schemas, runs transformations,
 * previews data, and loads results. BEFORE/AFTER_TOOL_CALL hooks build a full
 * audit trail, and the AGENT_END hook prints a summary.
 */
@Component
public class DataPipelinePlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(DataPipelinePlugin.class);

    private final List<AuditEntry> auditTrail = new ArrayList<>();

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "data-pipeline-plugin",
                "Data Pipeline Plugin",
                "Tools for ETL pipeline orchestration with audit trail",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new ValidateSchemaTool());
        api.registerTool(new RunTransformTool());
        api.registerTool(new PreviewDataTool());
        api.registerTool(new LoadDataTool());

        // Track timing for audit trail
        api.on(HookName.BEFORE_TOOL_CALL, (event, ctx) -> {
            if (event instanceof ToolCallEvent e) {
                auditTrail.add(new AuditEntry(
                        e.toolName(), e.parameters(), null,
                        Instant.now(), null, e.iterationNumber()));
                log.info("[AUDIT] Tool call started: {} (iteration {})", e.toolName(), e.iterationNumber());
            }
            return null;
        });

        api.on(HookName.AFTER_TOOL_CALL, (event, ctx) -> {
            if (event instanceof ToolCallEvent e) {
                // Update the last audit entry with result and end time
                if (!auditTrail.isEmpty()) {
                    var last = auditTrail.removeLast();
                    auditTrail.add(new AuditEntry(
                            last.toolName(), last.parameters(), e.result(),
                            last.startTime(), Instant.now(), last.iteration()));
                }
                log.info("[AUDIT] Tool call completed: {} (iteration {})", e.toolName(), e.iterationNumber());
            }
            return null;
        });

        // Print full audit summary at end of agent run
        api.on(HookName.AGENT_END, (event, ctx) -> {
            if (auditTrail.isEmpty()) return null;

            log.info("=== PIPELINE AUDIT SUMMARY ===");
            for (int i = 0; i < auditTrail.size(); i++) {
                var entry = auditTrail.get(i);
                String duration = entry.endTime() != null
                        ? Duration.between(entry.startTime(), entry.endTime()).toMillis() + "ms"
                        : "in-progress";
                log.info("  {}. {} [iter={}] — {} — params: {}",
                        i + 1, entry.toolName(), entry.iteration(), duration, entry.parameters());
            }
            log.info("=== END AUDIT ({} operations) ===", auditTrail.size());
            auditTrail.clear();
            return null;
        });
    }

    record AuditEntry(String toolName, String parameters, String result,
                      Instant startTime, Instant endTime, int iteration) {}

    // ---- Tools ----

    static class ValidateSchemaTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "validate_schema",
                    "Validate schema compatibility between source and target datasets",
                    "etl",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "source": { "type": "string", "description": "Source dataset or table name" },
                        "target": { "type": "string", "description": "Target dataset or table name" }
                      },
                      "required": ["source", "target"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String source = (String) parameters.get("source");
            String target = (String) parameters.get("target");

            return new ToolResult.Success("""
                    Schema Validation: %s -> %s
                    Status: COMPATIBLE

                    Field Mappings:
                      id (BIGINT)        -> id (BIGINT)          [exact match]
                      user_name (VARCHAR) -> username (VARCHAR)   [rename required]
                      created_at (TIMESTAMP) -> created_at (TIMESTAMP) [exact match]
                      amount (DECIMAL(10,2)) -> amount (DECIMAL(12,2)) [widening — safe]
                      status (VARCHAR)    -> status (ENUM)        [type narrowing — needs validation]

                    Warnings:
                      - Column 'user_name' requires rename to 'username'
                      - Column 'status' narrowing from VARCHAR to ENUM — values must match enum entries

                    Recommendation: Apply transform with rename + value validation before load.
                    """.formatted(source, target));
        }
    }

    static class RunTransformTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "run_transform",
                    "Execute a data transformation on a source dataset",
                    "etl",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "source": { "type": "string", "description": "Source dataset name" },
                        "transform_type": { "type": "string", "enum": ["clean", "normalize", "aggregate", "join"], "description": "Type of transformation" }
                      },
                      "required": ["source", "transform_type"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String source = (String) parameters.get("source");
            String transformType = (String) parameters.get("transform_type");

            return new ToolResult.Success("""
                    Transform: %s on %s
                    Status: SUCCESS

                    Input rows:  125,430
                    Output rows: 124,892
                    Dropped:     538 (0.43%%)

                    Applied operations:
                      1. Null removal — dropped 312 rows with null key fields
                      2. Deduplication — removed 226 duplicate entries
                      3. %s — applied type-specific transformations

                    Output preview available via preview_data tool.
                    """.formatted(transformType.toUpperCase(), source, transformType));
        }
    }

    static class PreviewDataTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "preview_data",
                    "Preview sample rows from a dataset",
                    "etl",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "source": { "type": "string", "description": "Dataset name to preview" },
                        "rows": { "type": "integer", "description": "Number of sample rows (default 5)" }
                      },
                      "required": ["source"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String source = (String) parameters.get("source");
            int rows = parameters.containsKey("rows") ? ((Number) parameters.get("rows")).intValue() : 5;

            var sb = new StringBuilder("Preview: %s (%d rows)\n\n".formatted(source, rows));
            sb.append("| id     | username     | created_at          | amount  | status   |\n");
            sb.append("|--------|-------------|---------------------|---------|----------|\n");

            String[][] sampleData = {
                    {"10001", "alice.chen", "2024-03-15 09:23:11", "1,250.00", "active"},
                    {"10002", "bob.smith", "2024-03-15 10:45:33", "890.50", "active"},
                    {"10003", "carol.wu", "2024-03-15 11:12:07", "2,100.75", "pending"},
                    {"10004", "dave.jones", "2024-03-15 12:01:45", "445.25", "active"},
                    {"10005", "eve.garcia", "2024-03-15 13:30:22", "3,200.00", "completed"}
            };

            int count = Math.min(rows, sampleData.length);
            for (int i = 0; i < count; i++) {
                sb.append("| %s | %s | %s | %s | %s |\n".formatted(
                        sampleData[i][0], sampleData[i][1], sampleData[i][2],
                        sampleData[i][3], sampleData[i][4]));
            }

            sb.append("\nTotal rows in dataset: 124,892\n");
            return new ToolResult.Success(sb.toString());
        }
    }

    static class LoadDataTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "load_data",
                    "Load transformed data into the target dataset. REQUIRES APPROVAL.",
                    "etl",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "source": { "type": "string", "description": "Source dataset name" },
                        "target": { "type": "string", "description": "Target dataset or table name" },
                        "mode": { "type": "string", "enum": ["append", "overwrite", "upsert"], "description": "Load mode" }
                      },
                      "required": ["source", "target", "mode"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String source = (String) parameters.get("source");
            String target = (String) parameters.get("target");
            String mode = (String) parameters.get("mode");

            return new ToolResult.Success("""
                    Load: %s -> %s (mode: %s)
                    Status: SUCCESS

                    Rows loaded:   124,892
                    Rows skipped:  0
                    Load duration: 4.7s

                    Post-load validation:
                      - Row count verified: 124,892 (match)
                      - Checksum verified: OK
                      - Constraints validated: OK
                      - Indexes rebuilt: 3 indexes (1.2s)

                    Pipeline complete. Target table '%s' is up to date.
                    """.formatted(source, target, mode.toUpperCase(), target));
        }
    }
}
