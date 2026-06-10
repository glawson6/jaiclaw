package io.jaiclaw.examples.salesenrich;

import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.StringJoiner;

@ShellComponent
public class SalesEnrichmentShellCommands {

    private final PipelineGateway gateway;
    private final PipelineExecutionTracker tracker;
    private final CrmLeadRepository repository;

    public SalesEnrichmentShellCommands(PipelineGateway gateway,
                                        PipelineExecutionTracker tracker,
                                        CrmLeadRepository repository) {
        this.gateway = gateway;
        this.tracker = tracker;
        this.repository = repository;
    }

    @ShellMethod(key = {"add-lead"},
            value = "Append a new lead to the in-memory queue. Args: <name> <company>")
    public String addLead(@ShellOption String name, @ShellOption String company) {
        repository.addLead(name, company);
        return "Queued lead (" + name + ", " + company + "). Queue size = " + repository.queueSize();
    }

    @ShellMethod(key = {"run-now"},
            value = "Fire one pipeline execution (pops one lead from the queue).")
    public String runNow() {
        if (repository.queueSize() == 0) {
            return "Queue is empty — `add-lead` first, or wait for the next CRON tick.";
        }
        PipelineExecutionHandle handle = gateway.trigger(SalesEnrichmentPipelines.PIPELINE_ID,
                "process-next-lead");
        return "Submitted executionId=" + handle.executionId()
                + ". Queue size after submit = " + repository.queueSize()
                + ". Run `executions` to follow progress.";
    }

    @ShellMethod(key = {"queue"}, value = "Show how many leads remain in the queue.")
    public String queue() {
        return "queueSize=" + repository.queueSize();
    }

    @ShellMethod(key = {"list-enriched"},
            value = "List every enriched-lead record this app has produced.")
    public String listEnriched() {
        List<String> records = repository.enriched();
        if (records.isEmpty()) {
            return "(no enriched leads yet — try `run-now`)";
        }
        return String.join("\n", records);
    }

    @ShellMethod(key = {"executions", "ph"}, value = "Show recent pipeline executions.")
    public String executions() {
        List<PipelineExecutionSummary> recent = tracker.recent(SalesEnrichmentPipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet)";
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
            value = "Pretty-print the most recent execution's per-stage durations.")
    public String lastResult() {
        List<PipelineExecutionSummary> recent = tracker.recent(SalesEnrichmentPipelines.PIPELINE_ID);
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
