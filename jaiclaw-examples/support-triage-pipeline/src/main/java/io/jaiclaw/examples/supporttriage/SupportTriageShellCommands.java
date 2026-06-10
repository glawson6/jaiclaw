package io.jaiclaw.examples.supporttriage;

import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.tracking.ExecutionStatus;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Spring Shell driver for the support-triage example.
 *
 * <p>Tiny vocabulary kept consistent across all jaiclaw pipeline examples:
 * <ul>
 *   <li>{@code trigger <text>} — submit a ticket through the pipeline</li>
 *   <li>{@code executions} (alias {@code ph}) — list recent pipeline executions</li>
 *   <li>{@code last-result} — pretty-print the most recent execution's final output</li>
 *   <li>{@code set-customer <id>} — pick which seeded customer the CRM stub will return</li>
 * </ul>
 */
@ShellComponent
public class SupportTriageShellCommands {

    private final PipelineGateway gateway;
    private final PipelineExecutionTracker tracker;

    public SupportTriageShellCommands(PipelineGateway gateway, PipelineExecutionTracker tracker) {
        this.gateway = gateway;
        this.tracker = tracker;
    }

    @ShellMethod(key = {"trigger", "ticket"}, value = "Submit a support ticket to the triage pipeline.")
    public String trigger(@ShellOption(arity = Integer.MAX_VALUE) String[] words) {
        String ticket = String.join(" ", words);
        PipelineExecutionHandle handle = gateway.trigger(SupportTriagePipelines.PIPELINE_ID, ticket);
        return "Submitted executionId=" + handle.executionId()
                + " (customer=" + SupportTriageBeans.CURRENT_CUSTOMER_ID.get() + ")."
                + " Run `history` or `last-result` to see the outcome.";
    }

    @ShellMethod(key = {"set-customer"}, value = "Choose which seeded customer the CRM stub returns (CUST-001..003).")
    public String setCustomer(@ShellOption String customerId) {
        SupportTriageBeans.CURRENT_CUSTOMER_ID.set(customerId);
        return "Current customer is now " + customerId
                + " (try CUST-001, CUST-002 for VIP escalation, CUST-003 for low tier).";
    }

    @ShellMethod(key = {"executions", "ph"}, value = "Show recent pipeline executions.")
    public String executions() {
        List<PipelineExecutionSummary> recent = tracker.recent(SupportTriagePipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet — run `trigger \"hello\"` first)";
        }
        StringJoiner sj = new StringJoiner("\n");
        sj.add(String.format("%-36s  %-9s  %6s  %s", "executionId", "status", "ms", "current/last stage"));
        for (PipelineExecutionSummary s : recent) {
            sj.add(String.format("%-36s  %-9s  %6s  %s",
                    s.executionId(),
                    s.status().name(),
                    formatDuration(s.totalDuration()),
                    s.currentStage() == null ? "-" : s.currentStage()));
        }
        return sj.toString();
    }

    @ShellMethod(key = {"last-result"}, value = "Print the most recent execution's final stage output.")
    public String lastResult() {
        List<PipelineExecutionSummary> recent = tracker.recent(SupportTriagePipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet)";
        }
        PipelineExecutionSummary last = recent.get(recent.size() - 1);
        Optional<PipelineExecutionSummary> full = tracker.byId(last.executionId());
        if (full.isEmpty()) {
            return "(no detail available for " + last.executionId() + ")";
        }
        PipelineExecutionSummary s = full.get();
        StringBuilder sb = new StringBuilder();
        sb.append("executionId=").append(s.executionId()).append('\n');
        sb.append("status=").append(s.status().name()).append('\n');
        if (s.totalDuration() != null) {
            sb.append("totalMs=").append(s.totalDuration().toMillis()).append('\n');
        }
        if (s.status() == ExecutionStatus.FAILED && s.failureReason() != null) {
            sb.append("failureReason=").append(s.failureReason()).append('\n');
        }
        sb.append("--- per-stage durations (ms) ---\n");
        s.stageDurations().forEach((name, dur) ->
                sb.append("  ").append(name).append(" = ").append(dur.toMillis()).append('\n'));
        return sb.toString();
    }

    private static String formatDuration(Duration d) {
        return d == null ? "-" : String.valueOf(d.toMillis());
    }
}
