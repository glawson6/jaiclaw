package io.jaiclaw.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 */
public class JsonFileTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileTaskStore.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public JsonFileTaskStore(Path storagePath) {
        this(storagePath, new TenantGuard(TenantProperties.DEFAULT));
    }

    public JsonFileTaskStore(Path storagePath, TenantGuard tenantGuard) {
        this.storePath = storagePath.resolve("tasks.json");
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
        } catch (IOException e) {
            log.warn("Failed to load tasks from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(tasks.values()));
        } catch (IOException e) {
            log.error("Failed to flush tasks to {}: {}", storePath, e.getMessage());
        }
    }
}
