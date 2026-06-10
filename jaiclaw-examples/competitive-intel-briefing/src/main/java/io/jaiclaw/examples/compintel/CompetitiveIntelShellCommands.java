package io.jaiclaw.examples.compintel;

import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

@ShellComponent
public class CompetitiveIntelShellCommands {

    private final PipelineGateway gateway;
    private final PipelineExecutionTracker tracker;
    private final CompetitiveIntelProperties properties;

    public CompetitiveIntelShellCommands(PipelineGateway gateway,
                                         PipelineExecutionTracker tracker,
                                         CompetitiveIntelProperties properties) {
        this.gateway = gateway;
        this.tracker = tracker;
        this.properties = properties;
    }

    @ShellMethod(key = {"run-now"}, value = "Fire the briefing pipeline immediately, not at the next cron tick.")
    public String runNow() {
        String input = "Generate briefing for competitors: " + String.join(", ", properties.competitors());
        PipelineExecutionHandle handle = gateway.trigger(CompetitiveIntelPipelines.PIPELINE_ID, input);
        return "Submitted executionId=" + handle.executionId()
                + ". The next scheduled cron tick will also run. Run `last-briefing` afterwards.";
    }

    @ShellMethod(key = {"competitors"}, value = "List currently tracked competitors.")
    public String competitors() {
        return String.join("\n", properties.competitors());
    }

    @ShellMethod(key = {"last-briefing"},
            value = "Print the most recent on-disk briefing markdown.")
    public String lastBriefing() throws IOException {
        if (!Files.isDirectory(CompetitiveIntelBeans.BRIEFINGS_DIR)) {
            return "(no briefings yet — run `run-now`)";
        }
        try (Stream<Path> stream = Files.list(CompetitiveIntelBeans.BRIEFINGS_DIR)) {
            List<Path> mds = new ArrayList<>();
            stream.filter(p -> p.toString().endsWith(".md")).forEach(mds::add);
            if (mds.isEmpty()) {
                return "(no briefings yet)";
            }
            mds.sort(Comparator.comparing(Path::getFileName));
            Path latest = mds.get(mds.size() - 1);
            return "--- " + latest.getFileName() + " ---\n" + Files.readString(latest);
        }
    }

    @ShellMethod(key = {"executions", "ph"}, value = "Show recent pipeline executions.")
    public String executions() {
        List<PipelineExecutionSummary> recent = tracker.recent(CompetitiveIntelPipelines.PIPELINE_ID);
        if (recent.isEmpty()) {
            return "(no executions yet — try `run-now`)";
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
}
