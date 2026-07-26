package io.jaiclaw.pipeline.web;

import io.jaiclaw.pipeline.render.PipelineHtmlRenderer;
import io.jaiclaw.pipeline.render.PipelineRenderService;
import io.jaiclaw.pipeline.render.RenderProfile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoints for pipeline visualization.
 *
 * <ul>
 *   <li>{@code GET /api/pipelines/{id}/render} — plain-text ASCII render.
 *       Query params: {@code view} (compact/table/flow, default compact),
 *       {@code execution} (specific execution id, defaults to most recent),
 *       {@code profile} (render width profile, default shell_80).</li>
 *   <li>{@code GET /api/pipelines/{id}/render.html} — HTML snippet.
 *       Query params: {@code view}, {@code execution}, {@code format}
 *       (div|svg — flow view only, default div).</li>
 * </ul>
 *
 * <p>Both endpoints return 404 for unknown pipeline ids. When the
 * pipeline exists but has no executions yet, the render is returned
 * with all stages in the PENDING state.
 */
@RestController
@RequestMapping("${jaiclaw.pipeline.http-trigger.base-path:/api/pipelines}")
public class PipelineRenderController {

    private final PipelineRenderService renderService;

    public PipelineRenderController(PipelineRenderService renderService) {
        this.renderService = renderService;
    }

    @GetMapping(value = "/{id}/render", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> renderAscii(
            @PathVariable("id") String pipelineId,
            @RequestParam(value = "view", required = false, defaultValue = "compact") String view,
            @RequestParam(value = "execution", required = false) String execution,
            @RequestParam(value = "profile", required = false, defaultValue = "shell_80") String profile) {
        if (!renderService.pipelineExists(pipelineId)) {
            return notFound(pipelineId, MediaType.APPLICATION_JSON);
        }
        String rendered = renderService.renderAscii(
                pipelineId,
                blankToNull(execution),
                PipelineRenderService.View.fromString(view),
                RenderProfile.fromString(profile));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(rendered);
    }

    @GetMapping(value = "/{id}/render.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> renderHtml(
            @PathVariable("id") String pipelineId,
            @RequestParam(value = "view", required = false, defaultValue = "compact") String view,
            @RequestParam(value = "execution", required = false) String execution,
            @RequestParam(value = "format", required = false, defaultValue = "div") String format) {
        if (!renderService.pipelineExists(pipelineId)) {
            return notFound(pipelineId, MediaType.APPLICATION_JSON);
        }
        String rendered = renderService.renderHtml(
                pipelineId,
                blankToNull(execution),
                PipelineRenderService.View.fromString(view),
                parseFormat(format));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(rendered);
    }

    private static PipelineHtmlRenderer.FlowFormat parseFormat(String value) {
        if (value == null || value.isBlank()) return PipelineHtmlRenderer.FlowFormat.DIV;
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return PipelineHtmlRenderer.FlowFormat.valueOf(v);
        } catch (IllegalArgumentException e) {
            return PipelineHtmlRenderer.FlowFormat.DIV;
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private ResponseEntity<?> notFound(String pipelineId, MediaType contentType) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(contentType)
                .body(Map.of("error", "pipeline not found: " + pipelineId));
    }
}
