package io.jclaw.cronmanager.shell;

import io.jclaw.core.model.CronJob;
import io.jclaw.cronmanager.CronJobManagerService;
import io.jclaw.cronmanager.model.CronExecutionRecord;
import io.jclaw.cronmanager.model.CronJobDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Spring Shell commands for cron job management.
 * Uses {@link ObjectProvider} for optional dependency on the manager service.
 */
@ShellComponent
public class CronJobCommands {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ObjectProvider<CronJobManagerService> managerServiceProvider;

    public CronJobCommands(ObjectProvider<CronJobManagerService> managerServiceProvider) {
        this.managerServiceProvider = managerServiceProvider;
    }

    @ShellMethod(key = "cron-list", value = "List all cron jobs")
    public String cronList() {
        CronJobManagerService service = requireService();
        List<CronJobDefinition> jobs = service.listJobs();

        if (jobs.isEmpty()) {
            return "No cron jobs configured.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-36s  %-20s  %-15s  %-8s  %-20s%n",
                "ID", "Name", "Schedule", "Enabled", "Next Run"));
        sb.append("-".repeat(105)).append("\n");

        for (CronJobDefinition def : jobs) {
            CronJob job = def.cronJob();
            sb.append(String.format("%-36s  %-20s  %-15s  %-8s  %-20s%n",
                    job.id(),
                    truncate(job.name(), 20),
                    job.schedule(),
                    job.enabled() ? "yes" : "no",
                    formatInstant(job.nextRunAt())));
        }
        return sb.toString();
    }

    @ShellMethod(key = "cron-show", value = "Show details of a specific cron job")
    public String cronShow(@ShellOption String jobId) {
        CronJobManagerService service = requireService();
        return service.getJob(jobId)
                .map(this::formatJobDetail)
                .orElse("Job not found: " + jobId);
    }

    @ShellMethod(key = "cron-create", value = "Create a new cron job")
    public String cronCreate(
            @ShellOption String name,
            @ShellOption String schedule,
            @ShellOption String prompt,
            @ShellOption(defaultValue = "default") String agentId,
            @ShellOption(defaultValue = "UTC") String timezone,
            @ShellOption(defaultValue = "true") boolean enabled) {

        CronJobManagerService service = requireService();

        CronJob cronJob = new CronJob(
                UUID.randomUUID().toString(), name, agentId, schedule, timezone,
                prompt, null, null, enabled, null, null);
        CronJobDefinition definition = new CronJobDefinition(cronJob);

        CronJobDefinition created = service.createJob(definition);
        return "Created cron job: " + created.cronJob().name() + " (id=" + created.id() + ")";
    }

    @ShellMethod(key = "cron-delete", value = "Delete a cron job")
    public String cronDelete(@ShellOption String jobId) {
        CronJobManagerService service = requireService();
        boolean deleted = service.deleteJob(jobId);
        return deleted ? "Deleted job: " + jobId : "Job not found: " + jobId;
    }

    @ShellMethod(key = "cron-run", value = "Execute a cron job immediately")
    public String cronRun(@ShellOption String jobId) {
        CronJobManagerService service = requireService();
        try {
            String runId = service.runNow(jobId);
            return "Launched execution for job " + jobId + " (runId=" + runId + ")";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @ShellMethod(key = "cron-history", value = "Show execution history for a cron job")
    public String cronHistory(
            @ShellOption String jobId,
            @ShellOption(defaultValue = "10") int limit) {

        CronJobManagerService service = requireService();
        List<CronExecutionRecord> history = service.getJobHistory(jobId, limit);

        if (history.isEmpty()) {
            return "No execution history for job: " + jobId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-36s  %-10s  %-20s  %-20s  %s%n",
                "Run ID", "Status", "Started", "Completed", "Result"));
        sb.append("-".repeat(120)).append("\n");

        for (CronExecutionRecord record : history) {
            sb.append(String.format("%-36s  %-10s  %-20s  %-20s  %s%n",
                    record.runId(),
                    record.status(),
                    formatInstant(record.startedAt()),
                    formatInstant(record.completedAt()),
                    truncate(record.result(), 40)));
        }
        return sb.toString();
    }

    @ShellMethod(key = "cron-pause", value = "Pause a cron job")
    public String cronPause(@ShellOption String jobId) {
        CronJobManagerService service = requireService();
        boolean paused = service.pauseJob(jobId);
        return paused ? "Paused job: " + jobId : "Job not found: " + jobId;
    }

    @ShellMethod(key = "cron-resume", value = "Resume a paused cron job")
    public String cronResume(@ShellOption String jobId) {
        CronJobManagerService service = requireService();
        boolean resumed = service.resumeJob(jobId);
        return resumed ? "Resumed job: " + jobId : "Job not found: " + jobId;
    }

    @ShellMethod(key = "cron-status", value = "Show cron manager status summary")
    public String cronStatus() {
        CronJobManagerService service = requireService();
        List<CronJobDefinition> jobs = service.listJobs();
        long enabledCount = jobs.stream()
                .filter(d -> d.cronJob().enabled())
                .count();

        return String.format("Cron Manager Status:%n  Total jobs: %d%n  Enabled: %d%n  Paused: %d",
                jobs.size(), enabledCount, jobs.size() - enabledCount);
    }

    private CronJobManagerService requireService() {
        CronJobManagerService service = managerServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Cron Job Manager service is not available");
        }
        return service;
    }

    private String formatJobDetail(CronJobDefinition def) {
        CronJob job = def.cronJob();
        return String.format("""
                Job: %s
                  ID:        %s
                  Agent:     %s
                  Schedule:  %s
                  Timezone:  %s
                  Prompt:    %s
                  Enabled:   %s
                  Profile:   %s
                  Last Run:  %s
                  Next Run:  %s""",
                job.name(), job.id(), job.agentId(), job.schedule(), job.timezone(),
                job.prompt(), job.enabled(), def.toolProfile(),
                formatInstant(job.lastRunAt()), formatInstant(job.nextRunAt()));
    }

    private static String formatInstant(Instant instant) {
        return instant != null ? FORMATTER.format(instant) : "-";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "-";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
