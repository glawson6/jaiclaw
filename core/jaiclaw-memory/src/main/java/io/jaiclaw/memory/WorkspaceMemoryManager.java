package io.jaiclaw.memory;

import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Manages workspace-based memory: reads/writes MEMORY.md and daily log files.
 * Memory is stored as human-readable markdown in the workspace directory.
 * <p>
 * In MULTI-tenant mode, memory files are stored per-tenant: {@code {baseDir}/{tenantId}/MEMORY.md}.
 * In SINGLE-tenant mode, paths remain flat: {@code {baseDir}/MEMORY.md}.
 */
public class WorkspaceMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMemoryManager.class);
    private static final String MEMORY_FILE = "MEMORY.md";

    private final Path workspaceDir;
    private final TenantGuard tenantGuard;

    public WorkspaceMemoryManager(Path workspaceDir) {
        this(workspaceDir, null);
    }

    public WorkspaceMemoryManager(Path workspaceDir, TenantGuard tenantGuard) {
        this.workspaceDir = workspaceDir;
        this.tenantGuard = tenantGuard;
    }

    public String readMemory() {
        Path memoryFile = resolveDir().resolve(MEMORY_FILE);
        if (!Files.exists(memoryFile)) return "";
        try {
            return Files.readString(memoryFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read MEMORY.md: {}", e.getMessage());
            return "";
        }
    }

    public void writeMemory(String content) {
        try {
            Path dir = resolveDir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(MEMORY_FILE), content,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to write MEMORY.md: {}", e.getMessage());
        }
    }

    public void appendToSection(String section, String content) {
        String memory = readMemory();
        String sectionHeader = "## " + section;
        int idx = memory.indexOf(sectionHeader);
        if (idx >= 0) {
            int nextSection = memory.indexOf("\n## ", idx + sectionHeader.length());
            String insertion = "\n- " + content;
            if (nextSection >= 0) {
                memory = memory.substring(0, nextSection) + insertion + memory.substring(nextSection);
            } else {
                memory = memory + insertion + "\n";
            }
        } else {
            memory = memory + "\n" + sectionHeader + "\n- " + content + "\n";
        }
        writeMemory(memory);
    }

    public Path getWorkspaceDir() {
        return workspaceDir;
    }

    private Path resolveDir() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.resolveTenantPrefix();
            return workspaceDir.resolve(tenantId);
        }
        return workspaceDir;
    }
}
