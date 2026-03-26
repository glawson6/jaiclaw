package io.jaiclaw.examples.sysmonitor;

import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.channel.telegram.SendTelegramTool;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.cronmanager.CronJobManagerService;
import io.jaiclaw.cronmanager.model.CronJobDefinition;
import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.tools.builtin.ShowAllowedCommandsTool;
import io.jaiclaw.tools.builtin.WhitelistedCommandConfig;
import io.jaiclaw.tools.builtin.WhitelistedCommandTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Wires the system monitor tools and registers the daily health report cron job.
 */
@Configuration
public class SystemMonitorConfig {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitorConfig.class);

    @Bean
    WhitelistedCommandConfig systemMonitorCommandConfig() {
        return new WhitelistedCommandConfig(
                List.of(
                        "uptime",
                        "free ",
                        "df ",
                        "top -bn1",
                        "vmstat",
                        "iostat",
                        "cat /proc/loadavg",
                        "cat /proc/meminfo",
                        "cat /proc/cpuinfo",
                        "ps aux",
                        "systemctl status",
                        "journalctl --no-pager",
                        "uname ",
                        "hostname",
                        "ip addr",
                        "ss -tlnp",
                        "lsblk",
                        "who",
                        "last -n",
                        "dmesg --ctime"
                ),
                30,   // timeout seconds
                200,  // max output lines
                "system_command",
                "System Monitor",
                Set.of(ToolProfile.FULL, ToolProfile.MINIMAL)
        );
    }

    @Bean
    WhitelistedCommandTool systemCommandTool(WhitelistedCommandConfig config) {
        return new WhitelistedCommandTool(config);
    }

    @Bean
    ShowAllowedCommandsTool showSystemCommands(WhitelistedCommandConfig config) {
        return new ShowAllowedCommandsTool(config, "show_system_commands");
    }

    @Bean
    SendTelegramTool sendTelegramTool(ChannelRegistry channelRegistry) {
        return new SendTelegramTool(channelRegistry);
    }

    /**
     * On startup, register the daily health report cron job via the cron manager.
     * The cron manager persists jobs in H2, so it will only be created once.
     */
    @Bean
    ApplicationRunner registerHealthReportJob(
            CronJobManagerService cronJobManagerService,
            @Value("${sysmonitor.schedule:0 7 * * *}") String schedule,
            @Value("${sysmonitor.timezone:America/New_York}") String timezone,
            @Value("${sysmonitor.telegram.chat-id:}") String chatId) {
        return args -> {
            // Only register if not already present (cron manager persists across restarts)
            if (cronJobManagerService.getJob("system-health-report").isPresent()) {
                log.info("system-health-report cron job already registered");
                return;
            }

            String prompt = buildPrompt(chatId);

            CronJob cronJob = new CronJob(
                    "system-health-report",
                    "Daily System Health Report",
                    "default",
                    schedule,
                    timezone,
                    prompt,
                    "telegram",
                    chatId,
                    true,
                    null,
                    null
            );

            CronJobDefinition definition = new CronJobDefinition(
                    cronJob, null, null, null, ToolProfile.MINIMAL, List.of());

            cronJobManagerService.createJob(definition);
            log.info("Registered system-health-report cron job (schedule={}, tz={})", schedule, timezone);
        };
    }

    private String buildPrompt(String chatId) {
        return """
                Generate a Linux system health report. Follow these steps:

                1. First call show_system_commands to see which monitoring commands are available
                2. Run the available commands to collect: uptime, memory usage, disk usage, \
                CPU load, and network listeners
                3. Analyze the results and format a concise health report with:
                   - Overall status (healthy/warning/critical)
                   - Uptime
                   - Memory usage (used/total, percentage)
                   - Disk usage per mount (used/total, percentage, flag any > 80%%)
                   - CPU load averages
                   - Top 5 processes by memory
                   - Any warnings or anomalies
                4. Send the formatted report to Telegram chat %s using the send_telegram tool
                """.formatted(chatId);
    }
}
