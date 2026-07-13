package io.jaiclaw.examples.aiops;

import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.shell.core.command.annotation.Option;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@Component
public class AiopsIncidentResponderShellCommands {

    private final PipelineGateway gateway;
    private final PipelineExecutionTracker tracker;

    public AiopsIncidentResponderShellCommands(PipelineGateway gateway, PipelineExecutionTracker tracker) {
        this.gateway = gateway;
        this.tracker = tracker;
    }

    @Command(name = "incident", alias = "alert", description = "Fire a simulated incident through the pipeline.")
    public String incident(@Option String[] words) {
        String text = (words == null || words.length == 0)
                ? "P2 alert: api-service 5xx rate at 12.4% for last 5 minutes (was 0.1%)."
                : String.join(" ", words);
        PipelineExecutionHandle handle = gateway.trigger(AiopsIncidentPipelines.PIPELINE_ID, text);
        return "Submitted executionId=" + handle.executionId() + ". Run `executions` or `last-result`.";
    }

    @Command(name = "replay", description = "Replay a previous execution by re-firing its original input.")
    public String replay(@Option String executionId) {
        Optional<PipelineExecutionSummary> prior = tracker.byId(executionId);
        if (prior.isEmpty()) {
            return "Unknown executionId: " + executionId;
        }
        // We don't preserve the body in the tracker — replay with a placeholder
        // so the demo at least shows that a new execution kicks off.
        PipelineExecutionHandle handle = gateway.trigger(AiopsIncidentPipelines.PIPELINE_ID,
                "REPLAY of " + executionId + " (original input not retained)");
        return "Re-fired as executionId=" + handle.executionId();
    }

    @Command(name = "executions", alias = "ph", description = "Show recent pipeline executions.")
    public String executions() {
        List<PipelineExecutionSummary> recent = tracker.recent(AiopsIncidentPipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet — try `incident`)";
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

    @Command(name = "last-result", description = "Print the most recent execution's final output.")
    public String lastResult() {
        List<PipelineExecutionSummary> recent = tracker.recent(AiopsIncidentPipelines.PIPELINE_ID);
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
