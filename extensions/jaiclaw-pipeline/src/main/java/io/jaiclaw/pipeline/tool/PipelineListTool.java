package io.jaiclaw.pipeline.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent tool {@code pipeline_list} — enumerate the pipelines registered
 * in the runtime. Same shape as {@code GET /actuator/pipelines} but
 * available inside a chat without an HTTP round-trip.
 */
public class PipelineListTool extends AbstractBuiltinTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {}
            }""";

    private final PipelineRegistry registry;

    public PipelineListTool(PipelineRegistry registry) {
        super(new ToolDefinition(
                "pipeline_list",
                "List every registered JaiClaw pipeline with its id, name, trigger type, and stage summary.",
                ToolCatalog.SECTION_CUSTOM, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.registry = registry;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext ctx) throws Exception {
        Collection<PipelineDefinition> all = registry.getAll();
        List<Map<String, Object>> out = new ArrayList<>(all.size());
        for (PipelineDefinition def : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", def.id());
            row.put("name", def.name());
            row.put("description", def.description());
            row.put("enabled", def.enabled());
            row.put("trigger", def.trigger() == null ? "MANUAL" : def.trigger().type().name());
            row.put("errorStrategy", def.errorStrategy() == null ? "STOP" : def.errorStrategy().name());
            int stageCount = def.stages() == null ? 0 : def.stages().size();
            row.put("stageCount", stageCount);
            if (def.stages() != null) {
                row.put("stageNames", def.stages().stream().map(s -> s.name()).toList());
            }
            out.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", out.size());
        payload.put("pipelines", out);
        return new ToolResult.Success(JSON.writeValueAsString(payload));
    }
}
