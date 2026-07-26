package io.jaiclaw.pipeline.render;

import io.jaiclaw.pipeline.tracking.ExecutionStatus;

import java.util.List;

/**
 * ASCII renderer for {@link PipelineViewModel} in three views: compact
 * (default, one-liner-per-stage), table (Unicode box-drawing table with
 * multiple columns), and flow (top-to-bottom stacked stage boxes with
 * arrows).
 *
 * <p>All three views respect the supplied {@link RenderProfile}'s width
 * so the output fits cleanly in the target chat/terminal.
 *
 * <p>Stateless; safe to reuse across threads.
 */
public class PipelineAsciiRenderer {

    /**
     * Padding on the stage-name column of the compact view so status +
     * duration line up regardless of stage-name length.
     */
    private static final int COMPACT_NAME_MIN_WIDTH = 16;

    // ── compact view ────────────────────────────────────────────

    public String renderCompact(PipelineViewModel vm, RenderProfile profile) {
        StringBuilder sb = new StringBuilder();
        // Header line
        sb.append("Pipeline: ").append(vm.pipelineId() == null ? "" : vm.pipelineId());
        if (vm.shortExecutionId() != null && !vm.shortExecutionId().isEmpty()) {
            sb.append(" (exec ").append(vm.shortExecutionId()).append(")");
        }
        sb.append('\n');

        int nameWidth = Math.max(COMPACT_NAME_MIN_WIDTH, longestName(vm.stages()) + 2);
        for (PipelineViewModel.Stage stage : vm.stages()) {
            sb.append("  [").append(stage.status().glyph()).append("] ");
            sb.append(padRight(stage.name(), nameWidth));
            sb.append(compactDurationCell(stage));
            sb.append('\n');
        }

        // Footer line
        sb.append("Status: ").append(overallStatusLabel(vm));
        if (vm.overallStatus() == ExecutionStatus.RUNNING && vm.currentStageIndex1Based() > 0) {
            sb.append(" (stage ").append(vm.currentStageIndex1Based())
                    .append('/').append(vm.totalStages()).append(')');
        } else if (vm.totalDuration() != null) {
            sb.append(" (").append(vm.totalDurationLabel()).append(" total)");
        }
        if (vm.failureReason() != null && !vm.failureReason().isBlank()) {
            sb.append('\n').append("  failure: ").append(truncate(vm.failureReason(), profile.width() - 11));
        }
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

    public String renderTable(PipelineViewModel vm, RenderProfile profile) {
        StringBuilder sb = new StringBuilder();

        // Header block
        sb.append("Pipeline: ").append(vm.pipelineId() == null ? "" : vm.pipelineId());
        if (vm.pipelineName() != null && !vm.pipelineName().isBlank()
                && !vm.pipelineName().equals(vm.pipelineId())) {
            sb.append(" — ").append(vm.pipelineName());
        }
        sb.append('\n');

        StringBuilder subHeader = new StringBuilder();
        if (vm.executionId() != null) subHeader.append("Execution: ").append(vm.shortExecutionId());
        if (vm.tenantId() != null) appendPipeCell(subHeader, "Tenant: " + vm.tenantId());
        appendPipeCell(subHeader, "Status: " + overallStatusLabel(vm));
        if (vm.totalDuration() != null) appendPipeCell(subHeader, "Total: " + vm.totalDurationLabel());
        if (subHeader.length() > 0) sb.append(subHeader).append('\n');
        sb.append('\n');

        // Column widths — leave a safety margin off the profile width for
        // outer borders / punctuation.
        int outputColMinWidth = 8;
        int stageColWidth = Math.max(6, longestName(vm.stages()) + 2);
        int typeColWidth = 11;   // "PROCESSOR" + padding
        int statusColWidth = 8;  // "✓ DONE"
        int durationColWidth = 10;
        int fixedCols = stageColWidth + typeColWidth + statusColWidth + durationColWidth;
        // 6 borders (│…│…│…│…│…│) + 5 inner separators
        int chromeWidth = 6 + 5 * 2;
        int outputColWidth = Math.max(outputColMinWidth,
                profile.width() - fixedCols - chromeWidth);

        String top = box('┌', '┬', '┐',
                stageColWidth + 2, typeColWidth + 2, statusColWidth + 2,
                durationColWidth + 2, outputColWidth + 2);
        String middle = box('├', '┼', '┤',
                stageColWidth + 2, typeColWidth + 2, statusColWidth + 2,
                durationColWidth + 2, outputColWidth + 2);
        String bottom = box('└', '┴', '┘',
                stageColWidth + 2, typeColWidth + 2, statusColWidth + 2,
                durationColWidth + 2, outputColWidth + 2);

        sb.append(top).append('\n');
        sb.append(tableRow(stageColWidth, typeColWidth, statusColWidth, durationColWidth, outputColWidth,
                "STAGE", "TYPE", "STATUS", "DURATION", "OUTPUT / FAILURE"));
        sb.append('\n').append(middle).append('\n');
        for (PipelineViewModel.Stage stage : vm.stages()) {
            String output = tableOutputCell(stage, outputColWidth);
            sb.append(tableRow(stageColWidth, typeColWidth, statusColWidth, durationColWidth, outputColWidth,
                    stage.name(),
                    stage.typeLabel(),
                    stage.status().glyph() + " " + stage.status().label(),
                    tableDurationCell(stage),
                    output));
            sb.append('\n');
        }
        sb.append(bottom).append('\n');
        return sb.toString();
    }

    private String tableOutputCell(PipelineViewModel.Stage stage, int width) {
        String content;
        if (stage.failureReason() != null && !stage.failureReason().isBlank()) {
            content = stage.failureReason();
        } else if (stage.outputPreview() != null && !stage.outputPreview().isBlank()) {
            content = stage.outputPreview();
        } else {
            content = "";
        }
        return truncate(content, width);
    }

    private String tableDurationCell(PipelineViewModel.Stage stage) {
        return switch (stage.status()) {
            case DONE, FAILED -> stage.durationLabel();
            case RUNNING -> "run…";
            case PENDING -> "—";
            case SKIPPED -> "skip";
        };
    }

    private String tableRow(int stageW, int typeW, int statusW, int durationW, int outputW,
                            String s, String t, String st, String d, String o) {
        return "│ " + padRight(s, stageW)
                + " │ " + padRight(t, typeW)
                + " │ " + padRight(st, statusW)
                + " │ " + padLeft(d, durationW)
                + " │ " + padRight(o, outputW)
                + " │";
    }

    private String box(char left, char sep, char right, int... colWidths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < colWidths.length; i++) {
            sb.append("─".repeat(colWidths[i]));
            sb.append(i == colWidths.length - 1 ? right : sep);
        }
        return sb.toString();
    }

    private void appendPipeCell(StringBuilder sb, String cell) {
        if (sb.length() > 0) sb.append(" | ");
        sb.append(cell);
    }

    // ── flow view ──────────────────────────────────────────────

    public String renderFlow(PipelineViewModel vm, RenderProfile profile) {
        StringBuilder sb = new StringBuilder();

        // Header line
        sb.append("Pipeline: ").append(vm.pipelineId() == null ? "" : vm.pipelineId());
        if (vm.shortExecutionId() != null && !vm.shortExecutionId().isEmpty()) {
            sb.append(" (exec ").append(vm.shortExecutionId()).append(")");
        }
        if (vm.overallStatus() != null) {
            sb.append(" — ").append(overallStatusLabel(vm));
        }
        sb.append('\n').append('\n');

        // Every node is a 3-line box; connector is 2 lines between them.
        // Box width sizing: fit the widest stage name + type + duration
        // plus 4 chars of padding + 2 border chars. Cap at profile width - 4
        // so the flow diagram sits inside the target width with a left
        // margin.
        int maxWidth = Math.max(20, profile.width() - 4);
        int desiredContent = 20;
        for (PipelineViewModel.Stage s : vm.stages()) {
            int line1 = s.status().glyph().length() + 1 + s.name().length();
            int line2 = s.typeLabel().length() + 2 + flowDurationCell(s).length();
            desiredContent = Math.max(desiredContent, Math.max(line1, line2));
        }
        int contentWidth = Math.min(maxWidth - 4, desiredContent);
        int boxWidth = contentWidth + 4;
        int connectorOffset = boxWidth / 2;

        List<PipelineViewModel.Stage> stages = vm.stages();
        for (int i = 0; i < stages.size(); i++) {
            PipelineViewModel.Stage stage = stages.get(i);
            boolean doubled = stage.status() == StageStatus.RUNNING;
            char tl = doubled ? '╔' : '┌';
            char tr = doubled ? '╗' : '┐';
            char bl = doubled ? '╚' : '└';
            char br = doubled ? '╝' : '┘';
            char h = doubled ? '═' : '─';
            char v = doubled ? '║' : '│';
            String hLine = String.valueOf(h).repeat(boxWidth - 2);

            String line1 = " " + stage.status().glyph() + " " + stage.name();
            String line2 = " " + stage.typeLabel() + "  " + flowDurationCell(stage);

            sb.append("  ").append(tl).append(hLine).append(tr).append('\n');
            sb.append("  ").append(v).append(padRight(line1, boxWidth - 2)).append(v).append('\n');
            sb.append("  ").append(v).append(padRight(line2, boxWidth - 2)).append(v).append('\n');
            if (stage.failureReason() != null && !stage.failureReason().isBlank()) {
                String failLine = " " + truncate(stage.failureReason(), boxWidth - 4);
                sb.append("  ").append(v).append(padRight(failLine, boxWidth - 2)).append(v).append('\n');
            }
            sb.append("  ").append(bl).append(hLine).append(br).append('\n');

            if (i < stages.size() - 1) {
                sb.append("  ").append(" ".repeat(connectorOffset)).append('│').append('\n');
                sb.append("  ").append(" ".repeat(connectorOffset)).append('▼').append('\n');
            }
        }
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

    // ── helpers ────────────────────────────────────────────────

    private static int longestName(List<PipelineViewModel.Stage> stages) {
        int max = 0;
        for (PipelineViewModel.Stage s : stages) {
            if (s.name() != null && s.name().length() > max) max = s.name().length();
        }
        return max;
    }

    private static String overallStatusLabel(PipelineViewModel vm) {
        if (vm.overallStatus() == null) return "NOT-RUN";
        return vm.overallStatus().name();
    }

    private static String padRight(String s, int width) {
        String v = s == null ? "" : s;
        if (v.length() >= width) return v.substring(0, width);
        return v + " ".repeat(width - v.length());
    }

    private static String padLeft(String s, int width) {
        String v = s == null ? "" : s;
        if (v.length() >= width) return v.substring(0, width);
        return " ".repeat(width - v.length()) + v;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        if (max <= 1) return "…";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
