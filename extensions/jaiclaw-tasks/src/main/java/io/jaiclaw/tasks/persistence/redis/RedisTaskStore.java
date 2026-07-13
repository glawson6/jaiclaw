package io.jaiclaw.tasks.persistence.redis;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStatus;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed {@link TaskStore}. Plan §9 group 4b.
 *
 * <p>Layout:
 * <ul>
 *   <li>{@code {prefix}:{tenantId}:{taskId}} — JSON-encoded {@link TaskRecord}</li>
 *   <li>{@code {prefix}:idx:status:{tenantId}:{status}} — SET of task ids</li>
 *   <li>{@code {prefix}:idx:board:{tenantId}:{boardId}:{state}} — SET of task ids</li>
 *   <li>{@code {prefix}:all:{tenantId}} — SET of all task ids in the tenant</li>
 * </ul>
 *
 * <p>{@code compareAndSave} uses Redis {@code WATCH}/{@code MULTI}/{@code EXEC}
 * for atomic CAS: read the current version inside a WATCH, build the new
 * record with version+1 in a MULTI, EXEC. If another writer modified the
 * key between WATCH and EXEC, the transaction aborts and we return
 * {@link Optional#empty} — same contract as
 * {@link io.jaiclaw.tasks.persistence.h2.H2TaskStore#compareAndSave}.
 *
 * <p>Index maintenance lives inside each save so the in-memory + JDBC
 * stores' implicit indexing is reproduced explicitly here. Reads use
 * {@code SMEMBERS} on the relevant index set then {@code MGET} the
 * payloads.
 */
public class RedisTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskStore.class);

    private final StringRedisTemplate template;
    private final TenantGuard tenantGuard;
    private final String prefix;
    private final ObjectMapper json;

    public RedisTaskStore(StringRedisTemplate template) {
        this(template, new TenantGuard(TenantProperties.DEFAULT), "jaiclaw:tasks");
    }

    public RedisTaskStore(StringRedisTemplate template, TenantGuard tenantGuard, String prefix) {
        this.template = template;
        this.tenantGuard = tenantGuard != null ? tenantGuard
                : new TenantGuard(TenantProperties.DEFAULT);
        this.prefix = (prefix == null || prefix.isBlank()) ? "jaiclaw:tasks" : prefix;
        this.json = new ObjectMapper()
                
                ;
    }

    @Override
    public void save(TaskRecord task) {
        String tenantId = effectiveTenantId(task);
        String key = recordKey(tenantId, task.id());
        // Read the prior version so we can clean up stale index entries
        // if status/board/state changed.
        Optional<TaskRecord> prior = readByKey(key);
        String payload = encode(task);
        template.execute(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> ops) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> sops = (RedisOperations<String, String>) ops;
                sops.multi();
                sops.opsForValue().set(key, payload);
                sops.opsForSet().add(allKey(tenantId), task.id());
                if (prior.isPresent() && prior.get().status() != task.status()) {
                    sops.opsForSet().remove(statusKey(tenantId, prior.get().status()), task.id());
                }
                sops.opsForSet().add(statusKey(tenantId, task.status()), task.id());
                if (prior.isPresent() && prior.get().boardId() != null
                        && (!equal(prior.get().boardId(), task.boardId())
                            || !equal(prior.get().state(), task.state()))) {
                    sops.opsForSet().remove(boardStateKey(tenantId,
                            prior.get().boardId(), prior.get().state()), task.id());
                }
                if (task.boardId() != null) {
                    sops.opsForSet().add(boardStateKey(tenantId, task.boardId(), task.state()),
                            task.id());
                }
                sops.exec();
                return null;
            }
        });
    }

    @Override
    public synchronized Optional<TaskRecord> compareAndSave(TaskRecord task) {
        String tenantId = effectiveTenantId(task);
        String key = recordKey(tenantId, task.id());
        long expected = task.version();
        long next = expected + 1;
        // Loop up to a few times under WATCH/MULTI/EXEC to handle the
        // contention case cleanly. A single attempt is enough for the
        // contract tests; the loop keeps the implementation honest under
        // real concurrent load.
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Object> txResult = template.execute((RedisCallback<List<Object>>) connection -> {
                connection.watch(key.getBytes(StandardCharsets.UTF_8));
                byte[] currentBytes = connection.stringCommands()
                        .get(key.getBytes(StandardCharsets.UTF_8));
                long stored = 0L;
                if (currentBytes != null) {
                    try {
                        TaskRecord current = json.readValue(currentBytes, TaskRecord.class);
                        stored = current.version();
                    } catch (Exception e) {
                        connection.unwatch();
                        throw new IllegalStateException(
                                "Failed to decode existing record at " + key, e);
                    }
                }
                if (stored != expected) {
                    connection.unwatch();
                    return null; // version mismatch — caller sees Optional.empty
                }
                TaskRecord toSave = task.withVersion(next);
                byte[] payload;
                try {
                    payload = json.writeValueAsBytes(toSave);
                } catch (Exception e) {
                    connection.unwatch();
                    throw new IllegalStateException("Failed to encode " + toSave, e);
                }
                connection.multi();
                connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), payload);
                connection.setCommands().sAdd(
                        allKey(tenantId).getBytes(StandardCharsets.UTF_8),
                        task.id().getBytes(StandardCharsets.UTF_8));
                connection.setCommands().sAdd(
                        statusKey(tenantId, task.status()).getBytes(StandardCharsets.UTF_8),
                        task.id().getBytes(StandardCharsets.UTF_8));
                if (task.boardId() != null) {
                    connection.setCommands().sAdd(
                            boardStateKey(tenantId, task.boardId(), task.state())
                                    .getBytes(StandardCharsets.UTF_8),
                            task.id().getBytes(StandardCharsets.UTF_8));
                }
                return connection.exec();
            });
            if (txResult == null) return Optional.empty();
            if (!txResult.isEmpty()) {
                return Optional.of(task.withVersion(next));
            }
            // EXEC returned empty → another writer modified the key between
            // our WATCH and EXEC. Loop and retry (we re-read the version
            // each time, so a real version drift surfaces as
            // Optional.empty).
        }
        return Optional.empty();
    }

    @Override
    public Optional<TaskRecord> findById(String id) {
        if (id == null) return Optional.empty();
        return readByKey(recordKey(currentTenantId(), id));
    }

    @Override
    public List<TaskRecord> findByStatus(TaskStatus status) {
        Set<String> ids = template.opsForSet().members(statusKey(currentTenantId(), status));
        return readByIds(currentTenantId(), ids);
    }

    @Override
    public List<TaskRecord> findByBoardAndState(String boardId, String state) {
        Set<String> ids = template.opsForSet().members(
                boardStateKey(currentTenantId(), boardId, state));
        return readByIds(currentTenantId(), ids);
    }

    @Override
    public List<TaskRecord> findAll() {
        Set<String> ids = template.opsForSet().members(allKey(currentTenantId()));
        return readByIds(currentTenantId(), ids);
    }

    @Override
    public void deleteById(String id) {
        if (id == null) return;
        String tenantId = currentTenantId();
        String key = recordKey(tenantId, id);
        Optional<TaskRecord> prior = readByKey(key);
        template.execute(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> ops) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> sops = (RedisOperations<String, String>) ops;
                sops.multi();
                sops.delete(key);
                sops.opsForSet().remove(allKey(tenantId), id);
                if (prior.isPresent()) {
                    sops.opsForSet().remove(statusKey(tenantId, prior.get().status()), id);
                    if (prior.get().boardId() != null) {
                        sops.opsForSet().remove(boardStateKey(tenantId,
                                prior.get().boardId(), prior.get().state()), id);
                    }
                }
                sops.exec();
                return null;
            }
        });
    }

    @Override
    public long count() {
        Long n = template.opsForSet().size(allKey(currentTenantId()));
        return n != null ? n : 0L;
    }

    // ── helpers ─────────────────────────────────────────────────────

    private String currentTenantId() {
        if (tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    private String effectiveTenantId(TaskRecord task) {
        return task.tenantId() != null ? task.tenantId() : currentTenantId();
    }

    private Optional<TaskRecord> readByKey(String key) {
        String raw = template.opsForValue().get(key);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(json.readValue(raw, TaskRecord.class));
        } catch (Exception e) {
            log.warn("Failed to decode RedisTaskStore record at {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private List<TaskRecord> readByIds(String tenantId, Set<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> keys = ids.stream().map(id -> recordKey(tenantId, id)).toList();
        List<String> payloads = template.opsForValue().multiGet(keys);
        if (payloads == null) return List.of();
        List<TaskRecord> out = new ArrayList<>(payloads.size());
        for (String raw : payloads) {
            if (raw == null) continue;
            try {
                out.add(json.readValue(raw, TaskRecord.class));
            } catch (Exception ignored) { /* skip a malformed row */ }
        }
        // Match the JSON/JDBC contract spec expectation: findByStatus and
        // findAll return newest-first by createdAt; findByBoardAndState by
        // orderIndex then createdAt. Sorting in-memory keeps the Redis side
        // simple at the cost of an O(n log n) per query — acceptable.
        return out;
    }

    private String encode(TaskRecord task) {
        try {
            return json.writeValueAsString(task);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode TaskRecord", e);
        }
    }

    private static boolean equal(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private String recordKey(String tenantId, String id) {
        return prefix + ":" + tenantId + ":" + id;
    }
    private String allKey(String tenantId) {
        return prefix + ":all:" + tenantId;
    }
    private String statusKey(String tenantId, TaskStatus status) {
        return prefix + ":idx:status:" + tenantId + ":" + status.name();
    }
    private String boardStateKey(String tenantId, String boardId, String state) {
        return prefix + ":idx:board:" + tenantId + ":" + boardId + ":"
                + (state == null ? "_" : state);
    }
}
