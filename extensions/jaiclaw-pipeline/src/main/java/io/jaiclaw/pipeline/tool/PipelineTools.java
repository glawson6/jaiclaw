package io.jaiclaw.pipeline.tool;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.render.PipelineRenderService;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory + registrar for the pipeline agent tools. Consumed by
 * {@code PipelineAutoConfiguration}'s {@code PipelineToolsRegistrar} bean.
 *
 * <p>Follows the {@code KanbanTools} pattern
 * ({@code extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/tool/KanbanTools.java})
 * — {@link #all(PipelineGateway, PipelineRegistry, PipelineExecutionTracker, PipelineRenderService)}
 * returns the tool instances (used by specs), while
 * {@link #registerAll(ToolRegistry, PipelineGateway, PipelineRegistry, PipelineExecutionTracker, PipelineRenderService)}
 * pushes them into the runtime's tool registry.
 *
 * <p>{@code renderService} may be {@code null} — in that case
 * {@code pipeline_render} is omitted from the returned list (the
 * renderer is optional, wired only when the render subsystem is on the
 * classpath).
 */
public final class PipelineTools {

    private PipelineTools() {}

    public static List<ToolCallback> all(
            PipelineGateway gateway,
            PipelineRegistry registry,
            PipelineExecutionTracker tracker,
            PipelineRenderService renderService) {
        if (renderService == null) {
            return List.of(
                    new PipelineListTool(registry),
                    new PipelineTriggerTool(gateway),
                    new PipelineStatusTool(tracker)
            );
        }
        return List.of(
                new PipelineListTool(registry),
                new PipelineTriggerTool(gateway),
                new PipelineStatusTool(tracker),
                new PipelineRenderTool(renderService)
        );
    }

    public static void registerAll(
            ToolRegistry toolRegistry,
            PipelineGateway gateway,
            PipelineRegistry registry,
            PipelineExecutionTracker tracker,
            PipelineRenderService renderService) {
        all(gateway, registry, tracker, renderService).forEach(toolRegistry::register);
    }
}
