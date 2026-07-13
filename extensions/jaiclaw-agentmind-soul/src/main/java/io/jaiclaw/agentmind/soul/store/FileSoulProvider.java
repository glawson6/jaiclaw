package io.jaiclaw.agentmind.soul.store;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.agent.StaleSoulVersionException;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JSON-on-disk {@link SoulProvider}. Scope-aware path selection per analysis
 * §5.3:
 *
 * <pre>
 *   TENANT  →  ${root}/{tenantId}/TENANT-SOUL.md
 *   AGENT   →  ${root}/{tenantId}/agents/{agentId}/SOUL.md
 * </pre>
 *
 * <p>SINGLE-mode collapse: when {@link TenantGuard#isMultiTenant()} is
 * {@code false}, the {@code {tenantId}} component is omitted and writes land
 * at {@code ${root}/TENANT-SOUL.md} / {@code ${root}/agents/{agentId}/SOUL.md}.
 *
 * <p>Per-key {@link ReentrantLock} serialises concurrent writes against the
 * same Soul to make the optimistic-CAS check race-free. Writes that fail the
 * CAS check raise {@link StaleSoulVersionException}.
 *
 * <p>Plan §5 tasks 1.5 + 1.15.
 */
public class FileSoulProvider implements SoulProvider {

    private static final Logger log = LoggerFactory.getLogger(FileSoulProvider.class);

    private final Path rootDir;
    private final TenantGuard tenantGuard;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FileSoulProvider(Path rootDir, TenantGuard tenantGuard, ObjectMapper mapper) {
        this.rootDir = rootDir;
        this.tenantGuard = tenantGuard;
        this.mapper = mapper;
    }

    @Override
    public Optional<Soul> findSoul(String tenantId, SoulScope scope, String agentId) {
        Path path = pathFor(tenantId, scope, agentId);
        if (!Files.exists(path)) return Optional.empty();
        try {
            String json = Files.readString(path);
            Soul stored = mapper.readValue(json, Soul.class);
            return Optional.of(stored);
        } catch (IOException e) {
            log.warn("Failed to read Soul at {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Soul saveSoul(Soul soul) {
        Path path = pathFor(soul.tenantId(), soul.scope(), soul.agentId());
        ReentrantLock lock = locks.computeIfAbsent(path.toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            Optional<Soul> existing = findSoul(soul.tenantId(), soul.scope(), soul.agentId());
            if (existing.isPresent() && soul.version() <= existing.get().version()) {
                throw new StaleSoulVersionException(existing.get().version() + 1, soul.version());
            }
            // Refresh lastModified server-side so clients can't backdate writes.
            Soul toPersist = new Soul(soul.scope(), soul.tenantId(), soul.agentId(),
                    soul.markdown(), Instant.now(), soul.version());
            atomicWrite(path, mapper.writeValueAsString(toPersist));
            return toPersist;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Soul at " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteSoul(String tenantId, SoulScope scope, String agentId) {
        Path path = pathFor(tenantId, scope, agentId);
        ReentrantLock lock = locks.computeIfAbsent(path.toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete Soul at {}: {}", path, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    Path pathFor(String tenantId, SoulScope scope, String agentId) {
        Path tenantPath = isMultiTenant() ? rootDir.resolve(tenantId) : rootDir;
        return switch (scope) {
            case TENANT -> tenantPath.resolve("TENANT-SOUL.md");
            case AGENT -> tenantPath.resolve("agents").resolve(agentId).resolve("SOUL.md");
        };
    }

    private boolean isMultiTenant() {
        return tenantGuard != null && tenantGuard.isMultiTenant();
    }

    private static void atomicWrite(Path path, String json) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json);
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
