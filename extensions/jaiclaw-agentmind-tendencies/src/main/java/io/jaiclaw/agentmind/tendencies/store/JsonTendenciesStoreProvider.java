package io.jaiclaw.agentmind.tendencies.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.agent.StaleTendenciesVersionException;
import io.jaiclaw.core.agent.TendenciesStoreProvider;
import io.jaiclaw.core.model.Tendencies;
import io.jaiclaw.core.model.TendenciesScope;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * JSON-on-disk {@link TendenciesStoreProvider}. Scope-aware path selection
 * per analysis §5.3:
 *
 * <pre>
 *   USER    →  ${root}/{tenantId}/users/{userKey}/TENDENCIES.json
 *   TENANT  →  ${root}/{tenantId}/TENANT-TENDENCIES.json
 * </pre>
 *
 * <p>SINGLE-mode collapse: when {@link TenantGuard#isMultiTenant()} is
 * {@code false} the {@code {tenantId}} component is omitted.
 *
 * <p>Files are stored as {@code .json} (not {@code .md}) because the
 * record carries both the markdown blob and the structured trait map —
 * the on-disk representation is the full Tendencies record, not just the
 * peerCardMarkdown.
 *
 * <p>Per-key {@link ReentrantLock} serialises concurrent writes against
 * the same record so the optimistic-CAS check is race-free.
 *
 * <p>Plan §8 task 3.3.
 */
public class JsonTendenciesStoreProvider implements TendenciesStoreProvider {

    public static final String TYPE = "json";

    private static final Logger log = LoggerFactory.getLogger(JsonTendenciesStoreProvider.class);
    private static final String USER_FILE = "TENDENCIES.json";
    private static final String TENANT_FILE = "TENANT-TENDENCIES.json";

    private final Path rootDir;
    private final TenantGuard tenantGuard;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public JsonTendenciesStoreProvider(Path rootDir, TenantGuard tenantGuard, ObjectMapper mapper) {
        this.rootDir = rootDir;
        this.tenantGuard = tenantGuard;
        this.mapper = mapper;
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public Optional<Tendencies> findTendencies(String tenantId, TendenciesScope scope,
                                                String canonicalUserId) {
        Path path = pathFor(tenantId, scope, canonicalUserId);
        if (!Files.exists(path)) return Optional.empty();
        try {
            String json = Files.readString(path);
            return Optional.of(mapper.readValue(json, Tendencies.class));
        } catch (IOException e) {
            log.warn("Failed to read Tendencies at {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Tendencies saveTendencies(Tendencies record) {
        Path path = pathFor(record.tenantId(), record.scope(), record.canonicalUserId());
        ReentrantLock lock = locks.computeIfAbsent(path.toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            Optional<Tendencies> existing = findTendencies(
                    record.tenantId(), record.scope(), record.canonicalUserId());
            if (existing.isPresent() && record.version() <= existing.get().version()) {
                throw new StaleTendenciesVersionException(
                        existing.get().version() + 1, record.version());
            }
            // Refresh updatedAt server-side so clients can't backdate.
            Tendencies toPersist = new Tendencies(record.scope(), record.tenantId(),
                    record.canonicalUserId(), record.peerCardMarkdown(), record.traits(),
                    Instant.now(), record.lastDialecticAt(),
                    record.dialecticPasses(), record.version());
            atomicWrite(path, mapper.writeValueAsString(toPersist));
            return toPersist;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Tendencies at " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteTendencies(String tenantId, TendenciesScope scope, String canonicalUserId) {
        Path path = pathFor(tenantId, scope, canonicalUserId);
        ReentrantLock lock = locks.computeIfAbsent(path.toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete Tendencies at {}: {}", path, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Collection<Tendencies> findActiveUsers(String tenantId, long sinceEpochMillis) {
        Path usersDir = tenantPath(tenantId).resolve("users");
        if (!Files.exists(usersDir)) return List.of();
        List<Tendencies> active = new ArrayList<>();
        Instant cutoff = Instant.ofEpochMilli(sinceEpochMillis);
        try (Stream<Path> userDirs = Files.list(usersDir)) {
            userDirs.forEach(userDir -> {
                Path file = userDir.resolve(USER_FILE);
                if (!Files.exists(file)) return;
                try {
                    String json = Files.readString(file);
                    Tendencies t = mapper.readValue(json, Tendencies.class);
                    if (!t.updatedAt().isBefore(cutoff)) {
                        active.add(t);
                    }
                } catch (IOException e) {
                    log.warn("Failed to read active-user Tendencies at {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list users dir {}: {}", usersDir, e.getMessage());
        }
        return active;
    }

    Path pathFor(String tenantId, TendenciesScope scope, String canonicalUserId) {
        Path tenantPath = tenantPath(tenantId);
        return switch (scope) {
            case TENANT -> tenantPath.resolve(TENANT_FILE);
            case USER -> tenantPath.resolve("users").resolve(canonicalUserId).resolve(USER_FILE);
        };
    }

    private Path tenantPath(String tenantId) {
        return isMultiTenant() ? rootDir.resolve(tenantId) : rootDir;
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
