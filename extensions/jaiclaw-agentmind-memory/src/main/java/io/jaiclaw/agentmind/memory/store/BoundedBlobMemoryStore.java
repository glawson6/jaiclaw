package io.jaiclaw.agentmind.memory.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.agent.StaleMemoryVersionException;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
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
 * JSON-on-disk {@link AgentMindMemoryProvider}. Scope-aware path selection per
 * analysis §5.3:
 *
 * <pre>
 *   TENANT  →  ${root}/{tenantId}/TENANT.md
 *   AGENT   →  ${root}/{tenantId}/agents/{agentId}/MEMORY.md
 *   PEER    →  ${root}/{tenantId}/users/{peerId}/agents/{agentId}/USER.md
 * </pre>
 *
 * <p>SINGLE-mode collapse: when {@link TenantGuard#isMultiTenant()} is
 * {@code false} the {@code {tenantId}} component is omitted from each path.
 *
 * <p>Per-key {@link ReentrantLock} serialises concurrent writes against the
 * same document so the optimistic-CAS check is race-free. Writes that fail
 * the CAS check raise {@link StaleMemoryVersionException}; writes whose
 * content exceeds {@link MemoryDocument#charBudget()} raise
 * {@link MemoryOverflowException} (hermes' error-as-control-flow pattern).
 *
 * <p>Plan §6 tasks 2.2 + 2.3.
 */
public class BoundedBlobMemoryStore implements AgentMindMemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(BoundedBlobMemoryStore.class);

    private final Path rootDir;
    private final TenantGuard tenantGuard;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public BoundedBlobMemoryStore(Path rootDir, TenantGuard tenantGuard, ObjectMapper mapper) {
        this.rootDir = rootDir;
        this.tenantGuard = tenantGuard;
        this.mapper = mapper;
    }

    @Override
    public Optional<MemoryDocument> findMemory(String tenantId, MemoryScope scope,
                                                String agentId, String peerId) {
        Path path = pathFor(tenantId, scope, agentId, peerId);
        if (!Files.exists(path)) return Optional.empty();
        try {
            String json = Files.readString(path);
            MemoryDocument stored = mapper.readValue(json, MemoryDocument.class);
            return Optional.of(stored);
        } catch (IOException e) {
            log.warn("Failed to read Memory at {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public MemoryDocument saveMemory(MemoryDocument doc) {
        if (doc.content().length() > doc.charBudget()) {
            throw new MemoryOverflowException(doc.scope(), doc.charBudget(), doc.content().length());
        }
        Path path = pathFor(doc.tenantId(), doc.scope(), doc.agentId(), doc.peerId());
        ReentrantLock lock = locks.computeIfAbsent(path.toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            Optional<MemoryDocument> existing = findMemory(doc.tenantId(), doc.scope(),
                    doc.agentId(), doc.peerId());
            if (existing.isPresent() && doc.version() <= existing.get().version()) {
                throw new StaleMemoryVersionException(existing.get().version() + 1, doc.version());
            }
            // Refresh timestamp server-side so clients cannot backdate writes.
            MemoryDocument toPersist = new MemoryDocument(doc.scope(), doc.tenantId(),
                    doc.agentId(), doc.peerId(), doc.content(), doc.charBudget(),
                    Instant.now(), doc.version());
            atomicWrite(path, mapper.writeValueAsString(toPersist));
            return toPersist;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Memory at " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteMemory(String tenantId, MemoryScope scope, String agentId, String peerId) {
        Path path = pathFor(tenantId, scope, agentId, peerId);
        ReentrantLock lock = locks.computeIfAbsent(path.toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete Memory at {}: {}", path, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    Path pathFor(String tenantId, MemoryScope scope, String agentId, String peerId) {
        Path tenantPath = isMultiTenant() ? rootDir.resolve(tenantId) : rootDir;
        return switch (scope) {
            case TENANT -> tenantPath.resolve("TENANT.md");
            case AGENT -> tenantPath.resolve("agents").resolve(agentId).resolve("MEMORY.md");
            case PEER -> tenantPath.resolve("users").resolve(peerId)
                    .resolve("agents").resolve(agentId).resolve("USER.md");
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
