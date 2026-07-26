package io.jaiclaw.shell.commands;

import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.render.PipelineHtmlRenderer;
import io.jaiclaw.pipeline.render.PipelineRenderService;
import io.jaiclaw.pipeline.render.RenderProfile;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Shell commands for JaiClaw pipeline operations.
 *
 * <p><b>Local vs remote.</b> When the pipeline module is on the shell's own
 * Spring classpath (embedded single-JVM usage — e.g. {@code bin/jaiclaw
 * chat} with pipelines enabled), the commands call
 * {@link PipelineGateway} / {@link PipelineRegistry} /
 * {@link PipelineExecutionTracker} beans directly for zero-latency,
 * zero-config invocation. When those beans aren't present (the shell is
 * a thin client talking to a remote gateway), the commands fall back to
 * HTTP calls against {@code jaiclaw.pipeline.shell.gateway-url}
 * (default {@code http://localhost:8080}).
 *
 * <p>Follows the {@code AuthCommand} template — one {@code @Component}
 * per verb group, {@link ObjectProvider} for optional beans, {@code String}
 * return values, ASCII-table formatting.
 *
 * <p>Guarded on {@link PipelineHtmlRenderer} being on the classpath. The
 * {@code jaiclaw-pipeline} module is an <em>optional</em> dep of
 * {@code jaiclaw-shell-commands}, so shells that don't include the pipeline
 * runtime (e.g. thin-client shells talking to a remote gateway) must not
 * try to register this bean — Spring's {@code @Component} introspection
 * force-loads referenced nested classes like
 * {@code PipelineHtmlRenderer.FlowFormat} and would crash the whole shell
 * context at boot.
 */
@Component
@ConditionalOnClass(PipelineHtmlRenderer.class)
public class PipelineCommand {

    private final ObjectProvider<PipelineGateway> gatewayProvider;
    private final ObjectProvider<PipelineRegistry> registryProvider;
    private final ObjectProvider<PipelineExecutionTracker> trackerProvider;
    private final ObjectProvider<PipelineRenderService> renderServiceProvider;
    private final String remoteBaseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public PipelineCommand(
            ObjectProvider<PipelineGateway> gatewayProvider,
            ObjectProvider<PipelineRegistry> registryProvider,
            ObjectProvider<PipelineExecutionTracker> trackerProvider,
            ObjectProvider<PipelineRenderService> renderServiceProvider,
            @Value("${jaiclaw.pipeline.shell.gateway-url:http://localhost:8080}")
            String remoteBaseUrl) {
        this.gatewayProvider = gatewayProvider;
        this.registryProvider = registryProvider;
        this.trackerProvider = trackerProvider;
        this.renderServiceProvider = renderServiceProvider;
        this.remoteBaseUrl = normalizeUrl(remoteBaseUrl);
    }

    // ── pipeline list ─────────────────────────────────────────────

    @Command(name = "pipeline list", alias = "pipeline-list",
            description = "List every registered JaiClaw pipeline (id, name, trigger, stages)")
    public String list() {
        PipelineRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            java.util.Collection<PipelineDefinition> defs = registry.getAll();
            return formatDefinitionTable(defs);
        }
        // Fallback — hit the remote actuator.
        return httpGet("/actuator/pipelines")
                .map(body -> "Pipelines (from " + remoteBaseUrl + "):\n" + body)
                .orElse("Could not reach pipeline registry (no local beans, "
                        + remoteBaseUrl + "/actuator/pipelines unreachable).");
    }

    // ── pipeline trigger ─────────────────────────────────────────

    @Command(name = "pipeline trigger", alias = "pipeline-trigger",
            description = "Trigger a pipeline. --await > 0 blocks for the completed result.")
    public String trigger(
            @Option(longName ="id", required = true,
                    description = "Pipeline id (see `pipeline list`)")
            String pipelineId,
            @Option(longName ="input", defaultValue = "",
                    description = "Payload for the first stage; empty when omitted")
            String input,
            @Option(longName ="await", defaultValue = "0",
                    description = "Block for this many seconds; 0 = fire-and-forget")
            int awaitSeconds,
            @Option(longName ="tenant", defaultValue = "",
                    description = "Tenant id (multi-tenant deployments)")
            String tenant,
            @Option(longName ="correlation", defaultValue = "",
                    description = "Optional correlation id for tracing")
            String correlation) {

        String tenantId = tenant.isBlank() ? null : tenant;
        String correlationId = correlation.isBlank() ? null : correlation;

        PipelineGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway != null) {
            if (awaitSeconds > 0) {
                try {
                    PipelineExecutionResult result = gateway.triggerAndAwait(
                            pipelineId, input, tenantId, correlationId,
                            Duration.ofSeconds(awaitSeconds));
                    return formatResult(result);
                } catch (Exception e) {
                    return "Trigger failed: " + e.getMessage();
                }
            }
            PipelineExecutionHandle handle;
            if (correlationId != null) {
                handle = gateway.trigger(pipelineId, input, tenantId, correlationId);
            } else if (tenantId != null) {
                handle = gateway.trigger(pipelineId, input, tenantId);
            } else {
                handle = gateway.trigger(pipelineId, input);
            }
            return formatHandle(handle);
        }
        // Remote fallback.
        String body = "{\"pipeline\":\"" + escapeJson(pipelineId)
                + "\",\"payload\":\"" + escapeJson(input) + "\"}";
        return httpPost("/api/pipelines/trigger", body)
                .map(r -> "Submitted (via " + remoteBaseUrl + "):\n" + r)
                .orElse("Trigger failed — no local gateway and "
                        + remoteBaseUrl + "/api/pipelines/trigger unreachable.");
    }

    // ── pipeline status ──────────────────────────────────────────

    @Command(name = "pipeline status", alias = "pipeline-status",
            description = "Show the current status of one pipeline execution")
    public String status(
            @Option(longName ="execution", required = true,
                    description = "The executionId returned from `pipeline trigger`")
            String executionId) {
        PipelineExecutionTracker tracker = trackerProvider.getIfAvailable();
        if (tracker != null) {
            Optional<PipelineExecutionSummary> found = tracker.byId(executionId);
            if (found.isEmpty()) {
                return "Execution not found in the tracker's bounded history: " + executionId;
            }
            return formatSummary(found.get());
        }
        return httpGet("/api/pipelines/status/" + urlEncode(executionId))
                .map(r -> "Status (via " + remoteBaseUrl + "):\n" + r)
                .orElse("Status unavailable — no local tracker and remote unreachable.");
    }

    // ── pipeline recent ──────────────────────────────────────────

    @Command(name = "pipeline recent", alias = "pipeline-recent",
            description = "Show the most-recent executions of one pipeline")
    public String recent(
            @Option(longName ="id", required = true,
                    description = "Pipeline id")
            String pipelineId,
            @Option(longName ="limit", defaultValue = "10",
                    description = "Maximum number of rows")
            int limit) {
        PipelineExecutionTracker tracker = trackerProvider.getIfAvailable();
        if (tracker != null) {
            List<PipelineExecutionSummary> all = tracker.recent(pipelineId);
            if (all == null || all.isEmpty()) {
                return "No recent executions for pipeline: " + pipelineId;
            }
            int take = Math.min(limit, all.size());
            return formatSummaryTable(all.subList(0, take));
        }
        return httpGet("/actuator/pipelines/" + urlEncode(pipelineId))
                .map(r -> "Recent (via " + remoteBaseUrl + "):\n" + r)
                .orElse("Recent history unavailable.");
    }

    // ── pipeline render ──────────────────────────────────────────

    @Command(name = "pipeline render", alias = "pipeline-render",
            description = "Render one pipeline as ASCII (default) or as an HTML snippet")
    public String render(
            @Option(longName ="id", required = true,
                    description = "Pipeline id (see `pipeline list`)")
            String pipelineId,
            @Option(longName ="execution", defaultValue = "",
                    description = "Specific execution id; defaults to the most recent")
            String execution,
            @Option(longName ="view", defaultValue = "compact",
                    description = "compact (default) | table | flow")
            String view,
            @Option(longName ="profile", defaultValue = "shell_80",
                    description = "Render width profile (shell_80, slack_desktop, telegram_mobile, ...)")
            String profile,
            @Option(longName ="format", defaultValue = "div",
                    description = "HTML flow format when --html is set (div | svg)")
            String format,
            @Option(longName ="html", defaultValue = "false",
                    description = "Emit an HTML snippet instead of ASCII")
            boolean html) {

        String executionId = execution.isBlank() ? null : execution;

        PipelineRenderService local = renderServiceProvider.getIfAvailable();
        if (local != null) {
            try {
                if (html) {
                    return local.renderHtml(pipelineId, executionId,
                            PipelineRenderService.View.fromString(view),
                            parseFlowFormat(format));
                }
                return local.renderAscii(pipelineId, executionId,
                        PipelineRenderService.View.fromString(view),
                        RenderProfile.fromString(profile));
            } catch (IllegalArgumentException e) {
                return "Render failed: " + e.getMessage();
            }
        }

        // Remote fallback via HTTP.
        StringBuilder query = new StringBuilder();
        query.append("view=").append(urlEncode(view));
        if (executionId != null) query.append("&execution=").append(urlEncode(executionId));
        if (html) {
            query.append("&format=").append(urlEncode(format));
            String path = "/api/pipelines/" + urlEncode(pipelineId) + "/render.html?" + query;
            return httpGet(path).orElse("Render unavailable via " + remoteBaseUrl);
        }
        query.append("&profile=").append(urlEncode(profile));
        String path = "/api/pipelines/" + urlEncode(pipelineId) + "/render?" + query;
        return httpGet(path).orElse("Render unavailable via " + remoteBaseUrl);
    }

    private static PipelineHtmlRenderer.FlowFormat parseFlowFormat(String value) {
        if (value == null || value.isBlank()) return PipelineHtmlRenderer.FlowFormat.DIV;
        try {
            return PipelineHtmlRenderer.FlowFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PipelineHtmlRenderer.FlowFormat.DIV;
        }
    }

    // ── pipeline watch ───────────────────────────────────────────

    @Command(name = "pipeline watch", alias = "pipeline-watch",
            description = "Tail the SSE event stream for one pipeline (ctrl-C to stop)")
    public String watch(
            @Option(longName ="id", required = true,
                    description = "Pipeline id (use * for the whole-system stream)")
            String pipelineId) {
        String path = "*".equals(pipelineId)
                ? "/api/pipelines/events"
                : "/api/pipelines/" + urlEncode(pipelineId) + "/events";
        String url = remoteBaseUrl + path;
        System.out.println("Watching " + url + " (ctrl-C to stop)");
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofHours(1))
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = http.send(
                    req, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                return "SSE connect failed: HTTP " + response.statusCode();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            return "Stream ended.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Watch interrupted.";
        } catch (Exception e) {
            return "Watch failed: " + e.getMessage();
        }
    }

    // ── formatting helpers ──────────────────────────────────────

    private String formatDefinitionTable(java.util.Collection<PipelineDefinition> defs) {
        if (defs.isEmpty()) return "No pipelines registered.";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-32s  %-10s  %-8s  %-6s  %s%n",
                "ID", "TRIGGER", "ENABLED", "STAGES", "NAME"));
        sb.append("-".repeat(80)).append('\n');
        for (PipelineDefinition d : defs) {
            String trigger = d.trigger() == null ? "MANUAL" : d.trigger().type().name();
            int stageCount = d.stages() == null ? 0 : d.stages().size();
            sb.append(String.format("%-32s  %-10s  %-8s  %-6d  %s%n",
                    truncate(d.id(), 32),
                    trigger,
                    d.enabled() ? "yes" : "no",
                    stageCount,
                    d.name() == null ? "" : d.name()));
        }
        sb.append('\n').append(defs.size()).append(" pipeline(s).");
        return sb.toString();
    }

    private String formatHandle(PipelineExecutionHandle handle) {
        return String.format(Locale.ROOT,
                "Submitted:%n  executionId: %s%n  pipelineId:  %s%n  submittedAt: %s%n",
                handle.executionId(), handle.pipelineId(), handle.submittedAt());
    }

    private String formatResult(PipelineExecutionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Execution %s%n", result.status()));
        sb.append(String.format("  executionId:  %s%n", result.executionId()));
        sb.append(String.format("  pipelineId:   %s%n", result.pipelineId()));
        if (result.tenantId() != null) sb.append(String.format("  tenantId:     %s%n", result.tenantId()));
        sb.append(String.format("  totalStages:  %d%n", result.totalStages()));
        sb.append(String.format("  duration:     %s%n",
                result.totalDuration() == null ? "-" : result.totalDuration()));
        if (result.failureReason() != null) {
            sb.append(String.format("  failed at:    %s%n", result.failureReason()));
        }
        if (result.stageOutputs() != null && !result.stageOutputs().isEmpty()) {
            sb.append("  outputs:\n");
            result.stageOutputs().forEach((k, v) ->
                    sb.append("    ").append(k).append(": ").append(truncate(v, 200)).append('\n'));
        }
        return sb.toString();
    }

    private String formatSummary(PipelineExecutionSummary s) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Execution %s%n", s.status()));
        sb.append(String.format("  executionId:  %s%n", s.executionId()));
        sb.append(String.format("  pipelineId:   %s%n", s.pipelineId()));
        if (s.tenantId() != null) sb.append(String.format("  tenantId:     %s%n", s.tenantId()));
        sb.append(String.format("  startedAt:    %s%n", s.startedAt()));
        sb.append(String.format("  completedAt:  %s%n", s.completedAt() == null ? "-" : s.completedAt()));
        sb.append(String.format("  duration:     %s%n", s.totalDuration() == null ? "-" : s.totalDuration()));
        if (s.currentStage() != null) sb.append(String.format("  currentStage: %s%n", s.currentStage()));
        if (s.failureReason() != null) sb.append(String.format("  failure:      %s%n", s.failureReason()));
        if (s.stageDurations() != null && !s.stageDurations().isEmpty()) {
            sb.append("  stages:\n");
            s.stageDurations().forEach((k, v) ->
                    sb.append("    ").append(k).append(": ").append(v).append('\n'));
        }
        return sb.toString();
    }

    private String formatSummaryTable(List<PipelineExecutionSummary> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-36s  %-10s  %-24s  %s%n",
                "EXECUTION ID", "STATUS", "STARTED", "DURATION"));
        sb.append("-".repeat(84)).append('\n');
        for (PipelineExecutionSummary s : rows) {
            sb.append(String.format("%-36s  %-10s  %-24s  %s%n",
                    s.executionId(), s.status(), s.startedAt(),
                    s.totalDuration() == null ? "-" : s.totalDuration()));
        }
        sb.append('\n').append(rows.size()).append(" execution(s).");
        return sb.toString();
    }

    // ── HTTP helpers ─────────────────────────────────────────────

    private Optional<String> httpGet(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(remoteBaseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 == 2) return Optional.of(resp.body());
            return Optional.of("HTTP " + resp.statusCode() + ": " + resp.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> httpPost(String path, String jsonBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(remoteBaseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 == 2) return Optional.of(resp.body());
            return Optional.of("HTTP " + resp.statusCode() + ": " + resp.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return "http://localhost:8080";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
