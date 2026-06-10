package io.jaiclaw.voicecall.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.voicecall.model.CallRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * JSONL append-only file store for call records. Each line is a JSON-serialized CallRecord.
 *
 * <p><b>Tenant isolation.</b> The on-disk layout is
 * {@code {baseDir}/{tenantId}/calls.jsonl}, so two tenants never write to the
 * same file. The in-memory index uses tenant-prefixed keys (the same convention
 * used by {@link InMemoryCallStore}). Async writes capture the calling thread's
 * tenant context via {@link TenantContextPropagator} so the writer thread
 * appends to the correct file even when the originating thread has moved on.
 *
 * <p>The constructor migrates an existing pre-tenancy {@code calls.jsonl} at
 * the base directory into {@code {baseDir}/{defaultTenantId}/calls.jsonl} on
 * first boot.
 */
public class JsonlCallStore implements CallStore {

    private static final Logger log = LoggerFactory.getLogger(JsonlCallStore.class);
    private static final String FILE_NAME = "calls.jsonl";

    /** Base directory; per-tenant calls.jsonl files live as {@code baseDir/{tenantId}/calls.jsonl}. */
    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final ExecutorService writeExecutor;
    private final TenantGuard tenantGuard;

    /** In-memory index keyed by tenant-prefixed callId: {@code {tenantId}:{callId}}. */
    private final Map<String, CallRecord> index = new ConcurrentHashMap<>();

    public JsonlCallStore(Path baseDir, TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        // If `baseDir` is the legacy single calls.jsonl file (not a directory),
        // its parent becomes the new base directory and we migrate the file in.
        Path resolvedBase = baseDir;
        try {
            if (Files.isRegularFile(baseDir) && baseDir.getFileName().toString().equals(FILE_NAME)) {
                resolvedBase = baseDir.getParent();
                migrateLegacyFile(baseDir, resolvedBase);
            } else {
                migrateLegacyFileIfPresent(baseDir);
            }
        } catch (IOException e) {
            log.warn("Could not migrate legacy {} at {}: {}", FILE_NAME, baseDir, e.getMessage());
        }
        this.baseDir = resolvedBase;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jsonl-call-store-writer");
            t.setDaemon(true);
            return t;
        });
        loadFromDisk();
    }

    /** Legacy single-arg constructor — uses a SINGLE-mode TenantGuard. */
    public JsonlCallStore(Path storePath) {
        this(storePath, new TenantGuard(TenantProperties.DEFAULT));
    }

    private String currentTenantId() {
        var ctx = TenantContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) return ctx.getTenantId();
        return tenantGuard.getProperties().defaultTenantId();
    }

    private String key(String tenantId, String callId) {
        return tenantId + ":" + callId;
    }

    private Path filePathFor(String tenantId) {
        return baseDir.resolve(tenantId).resolve(FILE_NAME);
    }

    @Override
    public void persist(CallRecord record) {
        String tenantId = record.getTenantId() != null ? record.getTenantId() : currentTenantId();
        if (record.getTenantId() == null) record.setTenantId(tenantId);
        index.put(key(tenantId, record.getCallId()), record);

        // Capture context so the async writer thread sees the right tenant.
        // We don't strictly need the captured context (the path is derived from
        // record.getTenantId()), but wrapping keeps downstream code that might
        // consult TenantGuard correct.
        Runnable wrapped = TenantContextPropagator.wrap(() -> appendToDisk(record));
        writeExecutor.submit(wrapped);
    }

    @Override
    public Map<String, CallRecord> loadActiveCalls() {
        if (TenantContextHolder.get() == null) {
            // Boot-time recovery — return everything keyed by the full tenant-scoped id.
            return index.entrySet().stream()
                    .filter(e -> !e.getValue().getState().isTerminal())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        String prefix = tenantGuard.resolveStoragePrefix() + ":";
        return index.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> !e.getValue().getState().isTerminal())
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(prefix.length()),
                        Map.Entry::getValue));
    }

    @Override
    public List<CallRecord> getHistory(int limit) {
        if (TenantContextHolder.get() == null) {
            return index.values().stream()
                    .sorted(Comparator.comparing(CallRecord::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        String prefix = tenantGuard.resolveStoragePrefix() + ":";
        return index.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(CallRecord::getStartedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void loadFromDisk() {
        if (!Files.isDirectory(baseDir)) return;
        try (var paths = Files.list(baseDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(tenantDir -> {
                        String tenantId = tenantDir.getFileName().toString();
                        Path file = tenantDir.resolve(FILE_NAME);
                        if (!Files.exists(file)) return;
                        loadTenantFile(tenantId, file);
                    });
        } catch (IOException e) {
            log.error("Failed to enumerate tenant directories in {}: {}", baseDir, e.getMessage());
        }
    }

    private void loadTenantFile(String tenantId, Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    CallRecord record = objectMapper.readValue(line, CallRecord.class);
                    if (record.getTenantId() == null) record.setTenantId(tenantId);
                    index.put(key(record.getTenantId(), record.getCallId()), record);
                    count++;
                } catch (Exception e) {
                    log.warn("Skipping malformed JSONL line in {}: {}", file, e.getMessage());
                }
            }
            log.info("Loaded {} call records for tenant {} from {}", count, tenantId, file);
        } catch (IOException e) {
            log.error("Failed to load call store from {}: {}", file, e.getMessage());
        }
    }

    private void appendToDisk(CallRecord record) {
        try {
            String tenantId = record.getTenantId() != null
                    ? record.getTenantId()
                    : tenantGuard.getProperties().defaultTenantId();
            Path file = filePathFor(tenantId);
            Files.createDirectories(file.getParent());
            String json = objectMapper.writeValueAsString(record);
            Files.writeString(file, json + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to persist call record {}: {}", record.getCallId(), e.getMessage());
        }
    }

    /**
     * If a legacy {@code baseDir/calls.jsonl} exists alongside the per-tenant
     * directory structure, move it into {@code baseDir/{defaultTenantId}/calls.jsonl}.
     * Idempotent: skipped if either the source is missing or the target already
     * exists.
     */
    private void migrateLegacyFileIfPresent(Path baseDir) throws IOException {
        if (!Files.isDirectory(baseDir)) return;
        Path legacy = baseDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(legacy)) return;
        migrateLegacyFile(legacy, baseDir);
    }

    private void migrateLegacyFile(Path legacy, Path resolvedBase) throws IOException {
        String defaultTenantId = tenantGuard.getProperties().defaultTenantId();
        Path tenantDir = resolvedBase.resolve(defaultTenantId);
        Path target = tenantDir.resolve(FILE_NAME);
        if (Files.exists(target)) {
            log.warn("Legacy {} found at {} but target {} already exists; leaving legacy in place",
                    FILE_NAME, legacy, target);
            return;
        }
        Files.createDirectories(tenantDir);
        Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE);
        log.info("Migrated legacy {} to {}", legacy, target);
    }

    public void shutdown() {
        writeExecutor.shutdown();
    }
}
