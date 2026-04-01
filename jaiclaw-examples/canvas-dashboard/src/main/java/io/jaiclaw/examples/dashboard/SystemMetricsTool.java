package io.jaiclaw.examples.dashboard;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Map;

/**
 * Returns live JVM and OS metrics (CPU load, memory, threads, uptime).
 * Uses {@link ManagementFactory} so the dashboard shows real data.
 */
public class SystemMetricsTool implements ToolCallback {

    private static final ToolDefinition DEF = new ToolDefinition(
            "get_system_metrics",
            "Get live system metrics including CPU load, memory usage, thread count, and uptime. "
                    + "Returns JSON with current JVM and OS statistics.",
            "dashboard"
    );

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long nonHeapUsed = memory.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);
        Duration uptime = Duration.ofMillis(runtime.getUptime());
        double cpuLoad = os.getSystemLoadAverage();

        String json = """
                {
                  "cpu": {
                    "availableProcessors": %d,
                    "systemLoadAverage": %.2f,
                    "arch": "%s"
                  },
                  "memory": {
                    "heapUsedMb": %d,
                    "heapMaxMb": %d,
                    "heapUsagePercent": %.1f,
                    "nonHeapUsedMb": %d
                  },
                  "threads": {
                    "live": %d,
                    "daemon": %d,
                    "peak": %d,
                    "totalStarted": %d
                  },
                  "runtime": {
                    "uptimeSeconds": %d,
                    "uptimeFormatted": "%s",
                    "javaVersion": "%s",
                    "vmName": "%s"
                  },
                  "os": {
                    "name": "%s",
                    "version": "%s"
                  }
                }""".formatted(
                os.getAvailableProcessors(),
                cpuLoad,
                os.getArch(),
                heapUsed,
                heapMax,
                heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0,
                nonHeapUsed,
                threads.getThreadCount(),
                threads.getDaemonThreadCount(),
                threads.getPeakThreadCount(),
                threads.getTotalStartedThreadCount(),
                uptime.getSeconds(),
                formatDuration(uptime),
                runtime.getSpecVersion(),
                runtime.getVmName(),
                os.getName(),
                os.getVersion()
        );

        return new ToolResult.Success(json);
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (hours > 0) {
            return "%dh %dm %ds".formatted(hours, minutes, seconds);
        } else if (minutes > 0) {
            return "%dm %ds".formatted(minutes, seconds);
        }
        return "%ds".formatted(seconds);
    }
}
