package io.jaiclaw.pipeline.render;

import io.jaiclaw.pipeline.tracking.ExecutionStatus;

import java.util.List;
import java.util.Locale;

/**
 * HTML-snippet renderer for {@link PipelineViewModel}.
 *
 * <p>Every method returns a self-contained HTML fragment (a single
 * {@code <div>} or {@code <svg>} root) that any SPA can drop into any
 * DOM location without CSS bleed. The snippet uses only inline
 * {@code style=""} attributes and CSS variables — <b>no</b>
 * {@code <style>} block, {@code <script>}, {@code <link>}, or external
 * asset URLs.
 *
 * <p><b>Class-name contract:</b> every element carries stable
 * {@code jaiclaw-pipeline-*} classes documented in the module plan
 * ({@code lazy-seeking-spindle.md} § 3 table). State variants like
 * {@code jaiclaw-pipeline-stage--done} sit alongside the generic
 * {@code jaiclaw-pipeline-stage} so consumers can style either way.
 * SPAs should target these class names for overrides; the inline styles
 * are defaults only.
 *
 * <p>Every stage-bearing element carries {@code data-stage},
 * {@code data-stage-status}, {@code data-stage-type},
 * {@code data-stage-index}, {@code data-duration-ms} attributes for
 * JS-driven selection and updates. The root carries
 * {@code data-pipeline-id}, {@code data-execution-id}, {@code data-view},
 * {@code data-status}.
 *
 * <p>Stateless; safe to reuse across threads.
 */
public class PipelineHtmlRenderer {

    /** Flow-view sub-format selector. */
    public enum FlowFormat {
        /** Nested {@code <div>} boxes with flexbox layout (default). */
        DIV,
        /** {@code <svg>} with rect nodes + line connectors. */
        SVG
    }

    /** Palette of CSS variables set on the root element of every render. */
    private static final String CSS_VARS = String.join("; ",
            "--jaiclaw-pipeline-font: ui-monospace, SFMono-Regular, 'Menlo', monospace",
            "--jaiclaw-pipeline-fg: #111827",
            "--jaiclaw-pipeline-muted: #6b7280",
            "--jaiclaw-pipeline-border: #d1d5db",
            "--jaiclaw-pipeline-status-pending: #6b7280",
            "--jaiclaw-pipeline-status-running: #d97706",
            "--jaiclaw-pipeline-status-done: #16a34a",
            "--jaiclaw-pipeline-status-failed: #dc2626",
            "--jaiclaw-pipeline-status-skipped: #9ca3af");

    // ── compact view ────────────────────────────────────────────

    public String renderCompact(PipelineViewModel vm) {
        StringBuilder sb = new StringBuilder(512);
        openRoot(sb, vm, "compact", "font-family: var(--jaiclaw-pipeline-font); color: var(--jaiclaw-pipeline-fg);");
        appendHeader(sb, vm);
        sb.append("<ul class=\"jaiclaw-pipeline-stage-list\" style=\"list-style: none; padding: 0; margin: 0.5em 0;\">");
        for (PipelineViewModel.Stage stage : vm.stages()) {
            String glyphColor = statusColorVar(stage.status());
            sb.append("<li class=\"")
                    .append(stageClasses(stage))
                    .append("\" ")
                    .append(stageDataAttrs(stage))
                    .append(" style=\"display: flex; align-items: baseline; gap: 0.75em; padding: 0.15em 0;\">");
            sb.append("<span class=\"jaiclaw-pipeline-stage-glyph\" style=\"font-weight: bold; color: ")
                    .append(glyphColor).append(";\">")
                    .append("[").append(HtmlEscaper.escape(stage.status().glyph())).append("]")
                    .append("</span>");
            sb.append("<span class=\"jaiclaw-pipeline-stage-name\" style=\"flex: 1 1 auto;\">")
                    .append(HtmlEscaper.escape(stage.name()))
                    .append("</span>");
            sb.append("<span class=\"jaiclaw-pipeline-stage-duration\" style=\"color: var(--jaiclaw-pipeline-muted);\">")
                    .append(HtmlEscaper.escape(compactDurationCell(stage)))
                    .append("</span>");
            sb.append("</li>");
        }
        sb.append("</ul>");
        appendFooter(sb, vm);
        closeRoot(sb);
        return sb.toString();
    }

    private String compactDurationCell(PipelineViewModel.Stage stage) {
        return switch (stage.status()) {
            case DONE, FAILED -> stage.durationLabel();
            case RUNNING -> "running";
            case PENDING -> "····";
            case SKIPPED -> "skipped";
        };
    }

    // ── table view ─────────────────────────────────────────────

    public String renderTable(PipelineViewModel vm) {
        StringBuilder sb = new StringBuilder(1024);
        openRoot(sb, vm, "table", "font-family: var(--jaiclaw-pipeline-font); color: var(--jaiclaw-pipeline-fg);");
        appendHeader(sb, vm);
        sb.append("<table class=\"jaiclaw-pipeline-table\" style=\"border-collapse: collapse; width: 100%; margin: 0.5em 0;\">");
        sb.append("<thead class=\"jaiclaw-pipeline-table-head\">");
        sb.append("<tr>");
        for (String[] col : new String[][]{
                {"stage", "STAGE"},
                {"type", "TYPE"},
                {"status", "STATUS"},
                {"duration", "DURATION"},
                {"output", "OUTPUT / FAILURE"}
        }) {
            sb.append("<th class=\"jaiclaw-pipeline-table-cell jaiclaw-pipeline-table-cell--")
                    .append(col[0])
                    .append("\" style=\"text-align: left; padding: 0.4em 0.6em; border-bottom: 1px solid var(--jaiclaw-pipeline-border); color: var(--jaiclaw-pipeline-muted); font-weight: 600;\">")
                    .append(col[1]).append("</th>");
        }
        sb.append("</tr>");
        sb.append("</thead>");
        sb.append("<tbody class=\"jaiclaw-pipeline-table-body\">");
        for (PipelineViewModel.Stage stage : vm.stages()) {
            String rowClasses = "jaiclaw-pipeline-table-row jaiclaw-pipeline-table-row--" + stage.status().cssModifier();
            sb.append("<tr class=\"").append(rowClasses).append("\" ")
                    .append(stageDataAttrs(stage))
                    .append(" style=\"border-bottom: 1px solid var(--jaiclaw-pipeline-border);\">");

            sb.append("<td class=\"jaiclaw-pipeline-table-cell jaiclaw-pipeline-table-cell--stage\" style=\"padding: 0.35em 0.6em; font-weight: 600;\">")
                    .append(HtmlEscaper.escape(stage.name())).append("</td>");

            sb.append("<td class=\"jaiclaw-pipeline-table-cell jaiclaw-pipeline-table-cell--type\" style=\"padding: 0.35em 0.6em;\">")
                    .append(HtmlEscaper.escape(stage.typeLabel())).append("</td>");

            sb.append("<td class=\"jaiclaw-pipeline-table-cell jaiclaw-pipeline-table-cell--status\" style=\"padding: 0.35em 0.6em; color: ")
                    .append(statusColorVar(stage.status())).append(";\">")
                    .append(HtmlEscaper.escape(stage.status().glyph()))
                    .append(' ')
                    .append(HtmlEscaper.escape(stage.status().label()))
                    .append("</td>");

            sb.append("<td class=\"jaiclaw-pipeline-table-cell jaiclaw-pipeline-table-cell--duration\" style=\"padding: 0.35em 0.6em; text-align: right;\">")
                    .append(HtmlEscaper.escape(tableDurationCell(stage))).append("</td>");

            String output;
            if (stage.failureReason() != null && !stage.failureReason().isBlank()) {
                output = "<span style=\"color: var(--jaiclaw-pipeline-status-failed);\">"
                        + HtmlEscaper.escape(stage.failureReason()) + "</span>";
            } else {
                output = HtmlEscaper.escape(stage.outputPreview() == null ? "" : stage.outputPreview());
            }
            sb.append("<td class=\"jaiclaw-pipeline-table-cell jaiclaw-pipeline-table-cell--output\" style=\"padding: 0.35em 0.6em; color: var(--jaiclaw-pipeline-muted);\">")
                    .append(output).append("</td>");

            sb.append("</tr>");
        }
        sb.append("</tbody>");
        sb.append("</table>");
        appendFooter(sb, vm);
        closeRoot(sb);
        return sb.toString();
    }

    private String tableDurationCell(PipelineViewModel.Stage stage) {
        return switch (stage.status()) {
            case DONE, FAILED -> stage.durationLabel();
            case RUNNING -> "run…";
            case PENDING -> "—";
            case SKIPPED -> "skip";
        };
    }

    // ── flow view ──────────────────────────────────────────────

    public String renderFlow(PipelineViewModel vm, FlowFormat format) {
        FlowFormat fmt = format == null ? FlowFormat.DIV : format;
        return switch (fmt) {
            case DIV -> renderFlowDiv(vm);
            case SVG -> renderFlowSvg(vm);
        };
    }

    private String renderFlowDiv(PipelineViewModel vm) {
        StringBuilder sb = new StringBuilder(1024);
        openRoot(sb, vm, "flow", "font-family: var(--jaiclaw-pipeline-font); color: var(--jaiclaw-pipeline-fg);");
        appendHeader(sb, vm);
        sb.append("<div class=\"jaiclaw-pipeline-flow\" style=\"display: flex; flex-direction: column; align-items: stretch; gap: 0; margin: 0.5em 0; max-width: 32em;\">");

        List<PipelineViewModel.Stage> stages = vm.stages();
        for (int i = 0; i < stages.size(); i++) {
            PipelineViewModel.Stage stage = stages.get(i);
            String color = statusColorVar(stage.status());
            String nodeClasses = "jaiclaw-pipeline-node jaiclaw-pipeline-node--"
                    + stage.status().cssModifier()
                    + " jaiclaw-pipeline-node--" + stageTypeCss(stage);
            sb.append("<div class=\"").append(nodeClasses).append("\" ")
                    .append(stageDataAttrs(stage))
                    .append(" style=\"border: 2px solid ").append(color)
                    .append("; border-radius: 6px; padding: 0.5em 0.75em; background: white;\">");
            sb.append("<div class=\"jaiclaw-pipeline-node-header\" style=\"display: flex; justify-content: space-between; gap: 0.5em; font-weight: 600;\">");
            sb.append("<span style=\"color: ").append(color).append(";\">")
                    .append(HtmlEscaper.escape(stage.status().glyph())).append(' ')
                    .append(HtmlEscaper.escape(stage.name()))
                    .append("</span>");
            sb.append("<span style=\"color: var(--jaiclaw-pipeline-muted); font-weight: 400; font-size: 0.85em;\">")
                    .append(HtmlEscaper.escape(stage.typeLabel())).append("</span>");
            sb.append("</div>");
            sb.append("<div class=\"jaiclaw-pipeline-node-body\" style=\"color: var(--jaiclaw-pipeline-muted); font-size: 0.9em; margin-top: 0.2em;\">");
            sb.append(HtmlEscaper.escape(flowDurationCell(stage)));
            if (stage.failureReason() != null && !stage.failureReason().isBlank()) {
                sb.append(" — <span style=\"color: var(--jaiclaw-pipeline-status-failed);\">")
                        .append(HtmlEscaper.escape(stage.failureReason()))
                        .append("</span>");
            }
            sb.append("</div>");
            sb.append("</div>");

            if (i < stages.size() - 1) {
                sb.append("<div class=\"jaiclaw-pipeline-connector\" style=\"text-align: center; color: var(--jaiclaw-pipeline-muted); font-size: 1.2em; line-height: 1; padding: 0.15em 0;\">▼</div>");
            }
        }
        sb.append("</div>");
        appendFooter(sb, vm);
        closeRoot(sb);
        return sb.toString();
    }

    private String renderFlowSvg(PipelineViewModel vm) {
        StringBuilder sb = new StringBuilder(1024);
        // SVG layout: node width 260, node height 60, gap 24. Total viewBox
        // height computed from stage count.
        int nodeWidth = 260;
        int nodeHeight = 60;
        int gap = 24;
        int connectorHeight = gap; // arrow lives inside the gap
        int padding = 16;
        int nodeCount = vm.stages().size();
        int contentHeight = nodeCount == 0 ? 0
                : nodeCount * nodeHeight + (nodeCount - 1) * connectorHeight;
        int width = nodeWidth + padding * 2;
        int height = contentHeight + padding * 2;

        sb.append("<div class=\"jaiclaw-pipeline jaiclaw-pipeline--flow-svg jaiclaw-pipeline--status-")
                .append(cssStatus(vm.overallStatus())).append("\" ")
                .append(rootDataAttrs(vm, "flow"))
                .append(" style=\"").append(CSS_VARS).append("\">");
        appendHeader(sb, vm);
        sb.append("<svg class=\"jaiclaw-pipeline-svg\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" xmlns=\"http://www.w3.org/2000/svg\" style=\"display: block; max-width: 100%;\">");

        int y = padding;
        for (int i = 0; i < nodeCount; i++) {
            PipelineViewModel.Stage stage = vm.stages().get(i);
            String color = statusColorVar(stage.status());
            sb.append("<g class=\"jaiclaw-pipeline-svg-node jaiclaw-pipeline-svg-node--")
                    .append(stage.status().cssModifier()).append("\" ")
                    .append(stageDataAttrs(stage)).append('>');
            sb.append("<rect class=\"jaiclaw-pipeline-svg-node-rect\" x=\"")
                    .append(padding).append("\" y=\"").append(y)
                    .append("\" width=\"").append(nodeWidth).append("\" height=\"").append(nodeHeight)
                    .append("\" rx=\"6\" ry=\"6\" fill=\"white\" stroke=\"").append(color)
                    .append("\" stroke-width=\"2\"/>");
            sb.append("<text class=\"jaiclaw-pipeline-svg-node-text\" x=\"")
                    .append(padding + 12).append("\" y=\"").append(y + 22)
                    .append("\" font-family=\"var(--jaiclaw-pipeline-font)\" font-size=\"14\" font-weight=\"600\" fill=\"")
                    .append(color).append("\">")
                    .append(HtmlEscaper.escape(stage.status().glyph())).append(' ')
                    .append(HtmlEscaper.escape(stage.name())).append("</text>");
            sb.append("<text class=\"jaiclaw-pipeline-svg-node-text\" x=\"")
                    .append(padding + 12).append("\" y=\"").append(y + 44)
                    .append("\" font-family=\"var(--jaiclaw-pipeline-font)\" font-size=\"11\" fill=\"var(--jaiclaw-pipeline-muted)\">")
                    .append(HtmlEscaper.escape(stage.typeLabel())).append(" · ")
                    .append(HtmlEscaper.escape(flowDurationCell(stage))).append("</text>");
            sb.append("</g>");

            if (i < nodeCount - 1) {
                int lineTop = y + nodeHeight;
                int lineBottom = lineTop + connectorHeight;
                int midX = padding + nodeWidth / 2;
                sb.append("<line class=\"jaiclaw-pipeline-svg-connector\" x1=\"").append(midX)
                        .append("\" y1=\"").append(lineTop)
                        .append("\" x2=\"").append(midX)
                        .append("\" y2=\"").append(lineBottom - 6)
                        .append("\" stroke=\"var(--jaiclaw-pipeline-border)\" stroke-width=\"2\"/>");
                // Arrowhead
                sb.append("<polygon class=\"jaiclaw-pipeline-svg-connector\" points=\"")
                        .append(midX - 4).append(',').append(lineBottom - 6).append(' ')
                        .append(midX + 4).append(',').append(lineBottom - 6).append(' ')
                        .append(midX).append(',').append(lineBottom)
                        .append("\" fill=\"var(--jaiclaw-pipeline-border)\"/>");
            }
            y += nodeHeight + connectorHeight;
        }
        sb.append("</svg>");
        appendFooter(sb, vm);
        sb.append("</div>");
        return sb.toString();
    }

    private String flowDurationCell(PipelineViewModel.Stage stage) {
        return switch (stage.status()) {
            case DONE, FAILED -> stage.durationLabel();
            case RUNNING -> "running";
            case PENDING -> "pending";
            case SKIPPED -> "skipped";
        };
    }

    // ── shared helpers ────────────────────────────────────────

    private void openRoot(StringBuilder sb, PipelineViewModel vm, String view, String extraStyle) {
        sb.append("<div class=\"jaiclaw-pipeline jaiclaw-pipeline--").append(view)
                .append(" jaiclaw-pipeline--status-").append(cssStatus(vm.overallStatus())).append("\" ")
                .append(rootDataAttrs(vm, view))
                .append(" style=\"").append(CSS_VARS);
        if (extraStyle != null && !extraStyle.isEmpty()) {
            sb.append("; ").append(extraStyle);
        }
        sb.append("\">");
    }

    private void closeRoot(StringBuilder sb) {
        sb.append("</div>");
    }

    private void appendHeader(StringBuilder sb, PipelineViewModel vm) {
        sb.append("<div class=\"jaiclaw-pipeline-header\" style=\"display: flex; flex-wrap: wrap; gap: 0.75em; align-items: baseline;\">");
        sb.append("<span class=\"jaiclaw-pipeline-name\" style=\"font-weight: 700;\">")
                .append(HtmlEscaper.escape(vm.pipelineId() == null ? "" : vm.pipelineId()))
                .append("</span>");
        if (vm.pipelineName() != null && !vm.pipelineName().isBlank()
                && !vm.pipelineName().equals(vm.pipelineId())) {
            sb.append("<span style=\"color: var(--jaiclaw-pipeline-muted);\">")
                    .append(HtmlEscaper.escape(vm.pipelineName()))
                    .append("</span>");
        }
        if (vm.executionId() != null && !vm.executionId().isEmpty()) {
            sb.append("<span class=\"jaiclaw-pipeline-execution-id\" style=\"color: var(--jaiclaw-pipeline-muted); font-size: 0.85em;\">exec ")
                    .append(HtmlEscaper.escape(vm.shortExecutionId()))
                    .append("</span>");
        }
        sb.append("</div>");
    }

    private void appendFooter(StringBuilder sb, PipelineViewModel vm) {
        sb.append("<div class=\"jaiclaw-pipeline-footer\" style=\"display: flex; gap: 0.75em; flex-wrap: wrap; margin-top: 0.5em; font-size: 0.9em;\">");
        String statusClass = "jaiclaw-pipeline-status jaiclaw-pipeline-status--" + cssStatus(vm.overallStatus());
        String statusColor = vm.overallStatus() == null
                ? "var(--jaiclaw-pipeline-muted)"
                : statusColorVar(overallToStage(vm.overallStatus()));
        sb.append("<span class=\"").append(statusClass).append("\" style=\"color: ")
                .append(statusColor).append("; font-weight: 600;\">")
                .append(HtmlEscaper.escape("Status: " + overallStatusLabel(vm)))
                .append("</span>");
        if (vm.totalDuration() != null) {
            sb.append("<span class=\"jaiclaw-pipeline-total-duration\" style=\"color: var(--jaiclaw-pipeline-muted);\">")
                    .append(HtmlEscaper.escape("Total: " + vm.totalDurationLabel()))
                    .append("</span>");
        }
        if (vm.failureReason() != null && !vm.failureReason().isBlank()) {
            sb.append("<span class=\"jaiclaw-pipeline-failure-reason\" style=\"color: var(--jaiclaw-pipeline-status-failed);\">")
                    .append(HtmlEscaper.escape(vm.failureReason())).append("</span>");
        }
        sb.append("</div>");
    }

    private String rootDataAttrs(PipelineViewModel vm, String view) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("data-pipeline-id=\"").append(HtmlEscaper.escape(vm.pipelineId() == null ? "" : vm.pipelineId())).append('"');
        sb.append(" data-execution-id=\"").append(HtmlEscaper.escape(vm.executionId() == null ? "" : vm.executionId())).append('"');
        sb.append(" data-view=\"").append(view).append('"');
        sb.append(" data-status=\"").append(cssStatus(vm.overallStatus())).append('"');
        return sb.toString();
    }

    private String stageClasses(PipelineViewModel.Stage stage) {
        return "jaiclaw-pipeline-stage jaiclaw-pipeline-stage--"
                + stage.status().cssModifier()
                + " jaiclaw-pipeline-stage--" + stageTypeCss(stage);
    }

    private String stageDataAttrs(PipelineViewModel.Stage stage) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("data-stage=\"").append(HtmlEscaper.escape(stage.name())).append('"');
        sb.append(" data-stage-status=\"").append(stage.status().cssModifier()).append('"');
        sb.append(" data-stage-type=\"").append(stageTypeCss(stage)).append('"');
        sb.append(" data-stage-index=\"").append(stage.index()).append('"');
        sb.append(" data-duration-ms=\"").append(stage.durationMs() == null ? "" : String.valueOf(stage.durationMs())).append('"');
        return sb.toString();
    }

    private static String stageTypeCss(PipelineViewModel.Stage stage) {
        return stage.type() == null ? "unknown" : stage.type().name().toLowerCase(Locale.ROOT);
    }

    private static String statusColorVar(StageStatus status) {
        return switch (status) {
            case PENDING -> "var(--jaiclaw-pipeline-status-pending)";
            case RUNNING -> "var(--jaiclaw-pipeline-status-running)";
            case DONE -> "var(--jaiclaw-pipeline-status-done)";
            case FAILED -> "var(--jaiclaw-pipeline-status-failed)";
            case SKIPPED -> "var(--jaiclaw-pipeline-status-skipped)";
        };
    }

    /**
     * Map an execution-level {@link ExecutionStatus} to an equivalent
     * per-stage status so the overall-status footer can reuse the same
     * color palette as the stage rows.
     */
    private static StageStatus overallToStage(ExecutionStatus status) {
        return switch (status) {
            case RUNNING -> StageStatus.RUNNING;
            case SUCCESS -> StageStatus.DONE;
            case FAILED -> StageStatus.FAILED;
        };
    }

    private static String cssStatus(ExecutionStatus status) {
        return status == null ? "not-run" : status.name().toLowerCase(Locale.ROOT);
    }

    private static String overallStatusLabel(PipelineViewModel vm) {
        if (vm.overallStatus() == null) return "NOT-RUN";
        return vm.overallStatus().name();
    }
}
