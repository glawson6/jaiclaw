package io.jaiclaw.examples.invoice;

import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;

@ShellComponent
public class InvoiceProcessorShellCommands {

    private final PipelineExecutionTracker tracker;

    public InvoiceProcessorShellCommands(PipelineExecutionTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Drop a synthetic invoice text file into the watched inbox so the FILE
     * trigger picks it up. The file content becomes the pipeline body.
     */
    @ShellMethod(key = {"inbox"},
            value = "Write a synthetic invoice file into the watched inbox to trigger the pipeline.")
    public String inbox(@ShellOption(arity = Integer.MAX_VALUE) String[] words) throws IOException {
        String body;
        if (words == null || words.length == 0) {
            // Default sample lets the user run `inbox` with no args.
            body = """
                    INVOICE #2025-0042
                    From: Acme Corp
                    PO: PO-1001
                    Amount: $1,250.00
                    Due: 2026-07-15
                    Line items:
                      - Widget A x10 @ $100.00
                      - Setup fee     $250.00
                    """;
        } else {
            body = String.join(" ", words);
        }
        Path target = InvoiceProcessorBeans.INBOX.resolve(
                "invoice-" + Instant.now().toEpochMilli() + ".txt");
        Files.writeString(target, body);
        return "Wrote " + target + " (" + body.length() + " chars). "
                + "Pipeline should pick it up within a second; run `executions`.";
    }

    @ShellMethod(key = {"list-approved"},
            value = "Print the approved-invoices JSONL file.")
    public String listApproved() throws IOException {
        if (!Files.exists(InvoiceProcessorBeans.APPROVED)) {
            return "(no approvals yet)";
        }
        return Files.readString(InvoiceProcessorBeans.APPROVED);
    }

    @ShellMethod(key = {"executions", "ph"}, value = "Show recent pipeline executions.")
    public String executions() {
        List<PipelineExecutionSummary> recent = tracker.recent(InvoiceProcessorPipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet — drop a file into the inbox first)";
        }
        StringJoiner sj = new StringJoiner("\n");
        sj.add(String.format("%-36s  %-9s  %6s", "executionId", "status", "ms"));
        for (PipelineExecutionSummary s : recent) {
            sj.add(String.format("%-36s  %-9s  %6s",
                    s.executionId(),
                    s.status().name(),
                    s.totalDuration() == null ? "-" : String.valueOf(s.totalDuration().toMillis())));
        }
        return sj.toString();
    }

    @ShellMethod(key = {"last-result"},
            value = "Print the most recent execution's final output.")
    public String lastResult() {
        List<PipelineExecutionSummary> recent = tracker.recent(InvoiceProcessorPipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet)";
        }
        PipelineExecutionSummary s = recent.get(recent.size() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append("executionId=").append(s.executionId()).append('\n');
        sb.append("status=").append(s.status().name()).append('\n');
        if (s.totalDuration() != null) sb.append("totalMs=").append(s.totalDuration().toMillis()).append('\n');
        if (s.failureReason() != null) sb.append("failureReason=").append(s.failureReason()).append('\n');
        sb.append("--- per-stage durations (ms) ---\n");
        s.stageDurations().forEach((name, dur) ->
                sb.append("  ").append(name).append(" = ").append(dur.toMillis()).append('\n'));
        return sb.toString();
    }
}
