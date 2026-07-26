package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.pipeline.PipelineDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-backed {@link PipelineDraftStore}. One JSON file per draft on
 * disk, tenant-scoped subdirectories:
 *
 * <pre>
 *   {basePath}/{tenantSegment}/{draftId}.json
 * </pre>
 *
 * where {@code tenantSegment} is the tenant id in MULTI mode and
 * empty (files land directly under {@code basePath}) in SINGLE mode.
 * Each write uses the {@code .tmp}-and-atomic-move pattern so a
 * partially-written file never appears on disk.
 *
 * <p>Envelope shape (via Jackson 3):
 *
 * <pre>{@code
 * { "id": "...",
 *   "revision": 3,
 *   "definition": { ...PipelineDefinition JSON... },
 *   "tenantId": "...",
 *   "status": "DRAFT",
 *   "origin": "STUDIO",
 *   "lastModifiedAt": "2026-07-20T..." }
 * }</pre>
 *
 * <p>Optimistic locking: {@link #save} reads the existing file, compares
 * {@code revision}, throws {@link OptimisticLockException} on
 * mismatch, otherwise writes {@code incoming.withNextRevision(...)}.
 */
public class JsonFilePipelineDraftStore implements PipelineDraftStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFilePipelineDraftStore.class);

    private final Path basePath;
    private final TenantGuard tenantGuard;
    private final ObjectMapper json;

    public JsonFilePipelineDraftStore(Path basePath) {
        this(basePath, new TenantGuard(TenantProperties.DEFAULT));
    }

    public JsonFilePipelineDraftStore(Path basePath, TenantGuard tenantGuard) {
        if (basePath == null) {
            throw new IllegalArgumentException("basePath must not be null");
        }
        this.basePath = basePath;
        this.tenantGuard = tenantGuard != null ? tenantGuard
                : new TenantGuard(TenantProperties.DEFAULT);
        this.json = new ObjectMapper();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create draft-store base path: " + basePath, e);
        }
    }

    @Override
    public Optional<PipelineDraft> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Path file = draftPath(id);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(readDraft(file));
    }

    @Override
    public List<PipelineDraft> findAll() {
        Path dir = tenantDir();
        if (!Files.isDirectory(dir)) return List.of();
        List<PipelineDraft> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp.json"))
                    .forEach(p -> {
                        try {
                            out.add(readDraft(p));
                        } catch (RuntimeException e) {
                            log.warn("Skipping unreadable draft at {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to list drafts under {}: {}", dir, e.getMessage());
        }
        return out;
    }

    @Override
    public PipelineDraft save(PipelineDraft draft) {
        if (draft == null) throw new IllegalArgumentException("draft must not be null");
        String currentTenant = resolveTenantId();
        PipelineDraft toWrite;
        Optional<PipelineDraft> existing = find(draft.id());
        if (existing.isPresent()) {
            PipelineDraft stored = existing.get();
            if (stored.revision() != draft.revision()) {
                throw new OptimisticLockException(draft.id(),
                        draft.revision(), stored.revision());
            }
            PipelineDefinition nextDefinition = draft.definition() != null
                    ? draft.definition() : stored.definition();
            toWrite = new PipelineDraft(
                    draft.id(),
                    stored.revision() + 1,
                    nextDefinition,
                    currentTenant,
                    draft.status(),
                    draft.origin(),
                    Instant.now());
        } else {
            toWrite = new PipelineDraft(
                    draft.id(),
                    draft.revision() > 0 ? draft.revision() : 1,
                    draft.definition(),
                    currentTenant,
                    draft.status(),
                    draft.origin(),
                    Instant.now());
        }
        writeDraft(toWrite);
        return toWrite;
    }

    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) return;
        Path file = draftPath(id);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete draft at {}: {}", file, e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────

    private Path tenantDir() {
        String segment = tenantSegment();
        return segment.isEmpty() ? basePath : basePath.resolve(segment);
    }

    private Path draftPath(String id) {
        return tenantDir().resolve(id + ".json");
    }

    private String tenantSegment() {
        if (!tenantGuard.isMultiTenant()) return "";
        String tenant = tenantGuard.resolveTenantPrefix();
        return tenant == null ? "" : tenant;
    }

    private String resolveTenantId() {
        return tenantGuard.requireTenantIfMulti();
    }

    private PipelineDraft readDraft(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return json.readValue(bytes, PipelineDraft.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read draft at " + file, e);
        }
    }

    private void writeDraft(PipelineDraft draft) {
        Path dir = tenantDir();
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(draft.id() + ".tmp.json");
            Path target = dir.resolve(draft.id() + ".json");
            byte[] bytes = json.writeValueAsBytes(draft);
            Files.write(tmp, bytes);
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write draft " + draft.id(), e);
        }
    }
}
