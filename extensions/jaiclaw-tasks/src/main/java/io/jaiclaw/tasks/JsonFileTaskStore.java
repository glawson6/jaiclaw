package io.jaiclaw.tasks;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JSON file-backed task store following the DocStore persistence pattern.
 *
 * <p>The in-memory map is keyed by {@code "{tenantId}:{taskId}"} so two
 * tenants using the same business-domain task id don't collide. Reads
 * filter by the current tenant prefix; writes always include it.
 *
 * <p>The on-disk file is a single {@code tasks.json} containing every
 * tenant's records. Each {@link TaskRecord} carries its {@code tenantId}
 * field, which is the canonical post-load tenant marker — the in-memory
 * key prefix is derived from that field when records are reloaded.
 *
 * <p>Writes are atomic: serialized to {@code tasks.json.tmp} then promoted
 * with {@link StandardCopyOption#ATOMIC_MOVE}, so a process crash mid-flush
 * cannot leave a truncated file in place. On parse failure during
 * {@code loadFromDisk}, the corrupt file is renamed to
 * {@code tasks.json.corrupt-<epochMillis>} and startup fails fast unless
 * {@code jaiclaw.tasks.storage.ignore-corrupt=true}.
 */
public class JsonFileTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileTaskStore.class);

    private final Path storePath;
    private final Path tmpPath;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;
    private final boolean ignoreCorrupt;

    public JsonFileTaskStore(Path storagePath) {
        this(storagePath, new TenantGuard(TenantProperties.DEFAULT), false);
    }

    public JsonFileTaskStore(Path storagePath, TenantGuard tenantGuard) {
        this(storagePath, tenantGuard, false);
    }

    public JsonFileTaskStore(Path storagePath, TenantGuard tenantGuard, boolean ignoreCorrupt) {
        this.storePath = storagePath.resolve("tasks.json");
        this.tmpPath = storagePath.resolve("tasks.json.tmp");
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        this.ignoreCorrupt = ignoreCorrupt;
        this.mapper = new ObjectMapper()
                
                ;
        loadFromDisk();
    }

    /** Resolve the storage key for a task, using its own tenantId when present. */
    private String storageKey(TaskRecord task) {
        String tenantId = task.tenantId() != null ? task.tenantId() : currentTenantId();
        return tenantId + ":" + task.id();
    }

    /** Resolve the storage key for the given task id under the current tenant. */
    private String storageKey(String taskId) {
        return currentTenantId() + ":" + taskId;
    }

    /** Current tenant id (default-tenant-id in SINGLE, throws in MULTI with no context). */
    private String currentTenantId() {
        if (tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    @Override
    public void save(TaskRecord task) {
        tasks.put(storageKey(task), task);
        flushToDisk();
    }

    @Override
    public synchronized Optional<TaskRecord> compareAndSave(TaskRecord task) {
        String key = storageKey(task);
        TaskRecord existing = tasks.get(key);
        long expected = task.version();
        long actual = existing != null ? existing.version() : 0L;
        if (existing != null && actual != expected) {
            return Optional.empty();
        }
        TaskRecord next = task.withVersion(actual + 1);
        tasks.put(key, next);
        flushToDisk();
        return Optional.of(next);
    }

    @Override
    public Optional<TaskRecord> findById(String id) {
        return Optional.ofNullable(tasks.get(storageKey(id)));
    }

    @Override
    public List<TaskRecord> findByStatus(TaskStatus status) {
        return currentTenantStream()
                .filter(t -> t.status() == status)
                .sorted(Comparator.comparing(TaskRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public List<TaskRecord> findByBoardAndState(String boardId, String state) {
        return currentTenantStream()
                .filter(t -> boardId.equals(t.boardId()))
                .filter(t -> state == null ? t.state() == null : state.equals(t.state()))
                .sorted(Comparator.comparingInt(TaskRecord::orderIndex)
                        .thenComparing(TaskRecord::createdAt))
                .toList();
    }

    @Override
    public List<TaskRecord> findAll() {
        return currentTenantStream()
                .sorted(Comparator.comparing(TaskRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        tasks.remove(storageKey(id));
        flushToDisk();
    }

    @Override
    public long count() {
        return currentTenantStream().count();
    }

    /** Records visible to the current tenant. */
    private java.util.stream.Stream<TaskRecord> currentTenantStream() {
        String prefix = currentTenantId() + ":";
        return tasks.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(java.util.Map.Entry::getValue);
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) return;
        try {
            List<TaskRecord> loaded = mapper.readValue(storePath.toFile(),
                    new TypeReference<List<TaskRecord>>() {});
            loaded.forEach(t -> tasks.put(storageKey(t), t));
            log.info("Loaded {} tasks from {}", tasks.size(), storePath);
        } catch (Exception e) {
            handleCorruptFile(e);
        }
    }

    private void handleCorruptFile(Exception cause) {
        Path quarantine = storePath.resolveSibling(
                storePath.getFileName().toString() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(storePath, quarantine, StandardCopyOption.ATOMIC_MOVE);
            log.error("Task store at {} was unreadable; quarantined to {}", storePath, quarantine);
        } catch (IOException quarantineFailure) {
            log.error("Task store at {} was unreadable and could not be quarantined: {}",
                    storePath, quarantineFailure.getMessage());
        }
        if (!ignoreCorrupt) {
            throw new IllegalStateException(
                    "Refusing to start with unreadable task store at " + storePath
                            + " — quarantined file at " + quarantine
                            + ". Set jaiclaw.tasks.storage.ignore-corrupt=true to start empty anyway.",
                    cause);
        }
        log.warn("ignore-corrupt is enabled; continuing with an empty task store");
    }

    private synchronized void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(tmpPath.toFile(), List.copyOf(tasks.values()));
            Files.move(tmpPath, storePath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("Failed to flush tasks to {}: {}", storePath, e.getMessage());
            throw new IllegalStateException("Failed to flush task store: " + storePath, e);
        }
    }
}
