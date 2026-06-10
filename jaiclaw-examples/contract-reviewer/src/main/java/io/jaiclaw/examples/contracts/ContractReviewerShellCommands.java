package io.jaiclaw.examples.contracts;

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
import java.util.Optional;
import java.util.StringJoiner;

@ShellComponent
public class ContractReviewerShellCommands {

    private final PipelineExecutionTracker tracker;

    public ContractReviewerShellCommands(PipelineExecutionTracker tracker) {
        this.tracker = tracker;
    }

    @ShellMethod(key = {"inbox"},
            value = "Drop a contract text into the watched inbox so the FILE trigger picks it up.")
    public String inbox(@ShellOption(arity = Integer.MAX_VALUE) String[] words) throws IOException {
        String body;
        if (words == null || words.length == 0) {
            body = """
                    MASTER SERVICES AGREEMENT
                    Parties: Acme Corp (Provider) and Globex Industries (Customer).
                    Effective: 2026-06-15.
                    Term: 24 months with automatic renewal unless 30 days written notice.
                    Payment: NET60 from invoice date.
                    Liability cap: $100,000 total aggregate.
                    Termination for convenience: 180 days written notice.
                    Indemnity: Provider indemnifies Customer; no mutual indemnity.
                    Jurisdiction: Delaware.
                    """;
        } else {
            body = String.join(" ", words);
        }
        Path target = ContractReviewerBeans.INBOX.resolve(
                "contract-" + Instant.now().toEpochMilli() + ".txt");
        Files.writeString(target, body);
        return "Wrote " + target + " (" + body.length() + " chars). "
                + "Pipeline should pick it up; run `executions` then `last-result`.";
    }

    @ShellMethod(key = {"show-redlines"},
            value = "Print the redline-stage output for a given execution.")
    public String showRedlines(@ShellOption String executionId) {
        Optional<PipelineExecutionSummary> summary = tracker.byId(executionId);
        if (summary.isEmpty()) {
            return "Unknown executionId: " + executionId;
        }
        return "(redlines are emitted to the log under "
                + "io.jaiclaw.pipeline.output.contract-reviewer — grep the application log for executionId=" + executionId + ")";
    }

    @ShellMethod(key = {"executions", "ph"}, value = "Show recent pipeline executions.")
    public String executions() {
        List<PipelineExecutionSummary> recent = tracker.recent(ContractReviewerPipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet — try `inbox`)";
        }
        StringJoiner sj = new StringJoiner("\n");
        sj.add(String.format("%-36s  %-9s  %6s", "executionId", "status", "ms"));
        for (PipelineExecutionSummary s : recent) {
            sj.add(String.format("%-36s  %-9s  %6s",
                    s.executionId(), s.status().name(),
                    s.totalDuration() == null ? "-" : String.valueOf(s.totalDuration().toMillis())));
        }
        return sj.toString();
    }

    @ShellMethod(key = {"last-result"},
            value = "Print the most recent execution's final output.")
    public String lastResult() {
        List<PipelineExecutionSummary> recent = tracker.recent(ContractReviewerPipelines.PIPELINE_ID);
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
