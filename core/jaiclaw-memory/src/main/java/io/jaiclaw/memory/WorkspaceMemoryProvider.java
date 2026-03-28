package io.jaiclaw.memory;

import io.jaiclaw.core.agent.MemoryProvider;
import io.jaiclaw.core.tenant.TenantGuard;

import java.nio.file.Path;

/**
 * Adapts the existing {@link WorkspaceMemoryManager} to the {@link MemoryProvider} SPI,
 * allowing the agent runtime to load workspace memory without a direct dependency on jaiclaw-memory.
 * In MULTI mode, memory is resolved per-tenant via {@link TenantGuard}.
 */
public class WorkspaceMemoryProvider implements MemoryProvider {

    private final TenantGuard tenantGuard;

    public WorkspaceMemoryProvider() {
        this(null);
    }

    public WorkspaceMemoryProvider(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    @Override
    public String loadMemory(String workspaceDir) {
        if (workspaceDir == null || workspaceDir.isBlank()) {
            return "";
        }
        var manager = new WorkspaceMemoryManager(Path.of(workspaceDir), tenantGuard);
        return manager.readMemory();
    }
}
