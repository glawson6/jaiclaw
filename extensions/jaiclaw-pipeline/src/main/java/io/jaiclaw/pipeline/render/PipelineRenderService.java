package io.jaiclaw.pipeline.render;

import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Composition entry point for pipeline rendering. Every LLM tool, REST
 * endpoint, and shell command routes through this service — resolves the
 * pipeline definition + latest execution, builds a
 * {@link PipelineViewModel}, and dispatches to the ASCII or HTML
 * renderer.
 *
 * <p>Handles two boundary cases uniformly:
 * <ul>
 *   <li><b>Unknown pipeline id</b> — throws {@link IllegalArgumentException}.
 *       The caller (REST controller / tool) maps that to a 404 / error
 *       result appropriate for its surface.</li>
 *   <li><b>Pipeline exists but has no executions</b> — returns a view
 *       with the definition-only shape (all stages PENDING, overall
 *       status null). Useful for showing the pipeline structure before
 *       it's ever run.</li>
 * </ul>
 */
public class PipelineRenderService {

    /** Supported ASCII/HTML view identifiers. */
    public enum View {
        COMPACT,
        TABLE,
        FLOW;

        public static View fromString(String value) {
            if (value == null || value.isBlank()) return COMPACT;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return COMPACT;
            }
        }
    }

    private final PipelineRegistry registry;
    private final PipelineExecutionTracker tracker;
    private final PipelineAsciiRenderer asciiRenderer;
    private final PipelineHtmlRenderer htmlRenderer;

    public PipelineRenderService(PipelineRegistry registry,
                                 PipelineExecutionTracker tracker,
                                 PipelineAsciiRenderer asciiRenderer,
                                 PipelineHtmlRenderer htmlRenderer) {
        this.registry = registry;
        this.tracker = tracker;
        this.asciiRenderer = asciiRenderer;
        this.htmlRenderer = htmlRenderer;
    }

    /**
     * Render one pipeline as ASCII in the given view + profile. When
     * {@code executionId} is null, resolves to the most recent execution
     * (or the definition-only view when no runs exist yet).
     */
    public String renderAscii(String pipelineId, String executionId, View view, RenderProfile profile) {
        PipelineViewModel vm = viewModel(pipelineId, executionId);
        View effectiveView = view == null ? View.COMPACT : view;
        RenderProfile effectiveProfile = profile == null ? RenderProfile.SHELL_80 : profile;
        return switch (effectiveView) {
            case COMPACT -> asciiRenderer.renderCompact(vm, effectiveProfile);
            case TABLE -> asciiRenderer.renderTable(vm, effectiveProfile);
            case FLOW -> asciiRenderer.renderFlow(vm, effectiveProfile);
        };
    }

    /**
     * Render one pipeline as an HTML snippet in the given view. The
     * {@code format} argument is only meaningful for {@link View#FLOW}
     * (chooses between nested {@code <div>} and {@code <svg>});
     * ignored for the other two views.
     */
    public String renderHtml(String pipelineId, String executionId, View view,
                             PipelineHtmlRenderer.FlowFormat format) {
        PipelineViewModel vm = viewModel(pipelineId, executionId);
        View effectiveView = view == null ? View.COMPACT : view;
        PipelineHtmlRenderer.FlowFormat effectiveFormat =
                format == null ? PipelineHtmlRenderer.FlowFormat.DIV : format;
        return switch (effectiveView) {
            case COMPACT -> htmlRenderer.renderCompact(vm);
            case TABLE -> htmlRenderer.renderTable(vm);
            case FLOW -> htmlRenderer.renderFlow(vm, effectiveFormat);
        };
    }

    /**
     * Build the joined view model for a pipeline + execution. Public so
     * callers who want the raw data (e.g. custom renderers, alternate
     * downstream formats like Mermaid or Graphviz) can bypass the built-in
     * renderers.
     *
     * @throws IllegalArgumentException when {@code pipelineId} is unknown
     */
    public PipelineViewModel viewModel(String pipelineId, String executionId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        PipelineDefinition definition = registry.get(pipelineId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown pipeline id: " + pipelineId);
        }
        PipelineExecutionSummary summary = resolveExecution(pipelineId, executionId);
        return PipelineViewModel.of(definition, summary);
    }

    private PipelineExecutionSummary resolveExecution(String pipelineId, String executionId) {
        if (tracker == null) {
            return null;
        }
        if (executionId != null && !executionId.isBlank()) {
            return tracker.byId(executionId).orElse(null);
        }
        // No explicit execution — grab the most-recent for this pipeline.
        List<PipelineExecutionSummary> recent = tracker.recent(pipelineId);
        if (recent == null || recent.isEmpty()) {
            return null;
        }
        // PipelineExecutionTracker.recent(...) returns newest-first per its
        // ring-buffer contract; front is the freshest.
        return recent.get(0);
    }

    /**
     * Whether a pipeline id is known to the registry. Convenience for the
     * REST controller's 404 pre-check that doesn't want to trigger the
     * IllegalArgumentException path.
     */
    public boolean pipelineExists(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) return false;
        return registry.get(pipelineId) != null;
    }

    /**
     * The most recent execution id for a pipeline, or empty when none
     * has run yet. Useful for the shell command's {@code --execution}
     * default resolution.
     */
    public Optional<String> mostRecentExecutionId(String pipelineId) {
        if (tracker == null) return Optional.empty();
        List<PipelineExecutionSummary> recent = tracker.recent(pipelineId);
        if (recent == null || recent.isEmpty()) return Optional.empty();
        return Optional.ofNullable(recent.get(0).executionId());
    }
}
