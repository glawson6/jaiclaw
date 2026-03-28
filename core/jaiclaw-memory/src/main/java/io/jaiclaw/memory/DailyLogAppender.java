package io.jaiclaw.memory;

import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Appends structured notes to daily markdown log files.
 * <p>
 * In SINGLE mode: {@code {baseDir}/memory/YYYY-MM-DD.md}.
 * In MULTI mode: {@code {baseDir}/{tenantId}/memory/YYYY-MM-DD.md}.
 */
public class DailyLogAppender {

    private static final Logger log = LoggerFactory.getLogger(DailyLogAppender.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final Path baseDir;
    private final TenantGuard tenantGuard;

    public DailyLogAppender(Path workspaceDir) {
        this(workspaceDir, null);
    }

    public DailyLogAppender(Path workspaceDir, TenantGuard tenantGuard) {
        this.baseDir = workspaceDir;
        this.tenantGuard = tenantGuard;
    }

    private Path resolveMemoryDir() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return baseDir.resolve(tenantGuard.resolveTenantPrefix()).resolve("memory");
        }
        return baseDir.resolve("memory");
    }

    public void append(String note) {
        append(LocalDate.now(), note);
    }

    public void append(LocalDate date, String note) {
        try {
            Path memoryDir = resolveMemoryDir();
            Files.createDirectories(memoryDir);
            Path logFile = memoryDir.resolve(date + ".md");

            boolean isNew = !Files.exists(logFile);
            String timestamp = LocalTime.now().format(TIME_FORMAT);
            String entry = (isNew ? "# " + date + "\n\n" : "")
                    + "- **" + timestamp + "** " + note + "\n";

            Files.writeString(logFile, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to append daily log for {}: {}", date, e.getMessage());
        }
    }

    public String readLog(LocalDate date) {
        Path logFile = resolveMemoryDir().resolve(date + ".md");
        if (!Files.exists(logFile)) return "";
        try {
            return Files.readString(logFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read daily log {}: {}", date, e.getMessage());
            return "";
        }
    }

    public Path getMemoryDir() {
        return resolveMemoryDir();
    }
}
